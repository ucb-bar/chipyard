#include "tracerv_dwarf.h"

#include <cinttypes>
#include <cstdint>
#include <cstdio>
#include <map>
#include <memory>
#include <stdexcept>
#include <vector>

#include <dwarf.h>
#include <libdwarf.h>

namespace {
void dwarf_runtime_error(Dwarf_Error err, Dwarf_Ptr arg) {
  std::string msg("dwarf: ");
  msg.append((dwarf_errno(err) == DW_DLA_ERROR) ? dwarf_errmsg(err)
                                                : "unspecified error");
  throw std::runtime_error(msg);
}
} // namespace

// Custom deleter for libdwarf descriptors
class dwarf_t::dwarf_deleter {
public:
  dwarf_deleter(Dwarf_Debug dbg) : dbg(dbg) {}

  void operator()(Dwarf_Die die) const {
    dwarf_dealloc(this->dbg, die, DW_DLA_DIE);
  }

  void operator()(Dwarf_Attribute attr) const {
    dwarf_dealloc(this->dbg, attr, DW_DLA_ATTR);
  }

  void operator()(char *str) const {
    dwarf_dealloc(this->dbg, str, DW_DLA_STRING);
  }

private:
  Dwarf_Debug dbg;
};

dwarf_t::dwarf_t(Elf *elf) {
  Dwarf_Error err;
  if (dwarf_elf_init(
          elf, DW_DLC_READ, &dwarf_runtime_error, nullptr, &this->dbg, &err) !=
      DW_DLV_OK) {
    this->dbg = nullptr;
  }
}

void dwarf_t::subroutines(subroutine_map &table) {
  if (this->dbg == nullptr) {
    return;
  }
  Dwarf_Unsigned next_cu_offset;
  while (dwarf_next_cu_header_c(this->dbg,
                                1,       // is_info
                                nullptr, // cu_header_length
                                nullptr, // version_stamp
                                nullptr, // abbrev_offset
                                nullptr, // address_size
                                nullptr, // offset_size
                                nullptr, // extension_size
                                nullptr, // signature
                                nullptr, // typeoffset
                                &next_cu_offset,
                                nullptr) == DW_DLV_OK) {

    // Expect CU to have an initial DIE
    Dwarf_Die die;
    if (dwarf_siblingof(this->dbg, nullptr, &die, nullptr) != DW_DLV_OK) {
      continue;
    }
    die_ptr die_wrap(die, dwarf_deleter(dbg));

    if (dwarf_child(die, &die, nullptr) == DW_DLV_OK) {
      die_wrap = die_ptr(die, dwarf_deleter(dbg));
      // Enumerate subprograms
      this->siblings(std::move(die_wrap), &dwarf_t::die_subprogram, table);
    }
  }
}

// Traverse siblings of a given DIE
template <typename T>
void dwarf_t::siblings(die_ptr die,
                       void (dwarf_t::*fn)(Dwarf_Die, T &),
                       T &arg) {
  for (;;) {
    Dwarf_Die p = die.get();

    (this->*fn)(p, arg);
    if (dwarf_siblingof(this->dbg, p, &p, nullptr) != DW_DLV_OK) {
      return;
    }
    die = die_ptr(p, dwarf_deleter(dbg));
  }
}

void dwarf_t::die_callsite(Dwarf_Die die, std::vector<callsite_t> &table) {
  Dwarf_Die child;

  if (dwarf_child(die, &child, nullptr) == DW_DLV_OK) {
    die_ptr die_wrap(child, dwarf_deleter(dbg));
    this->siblings(std::move(die_wrap), &dwarf_t::die_callsite, table);
    return;
  }

  Dwarf_Half tag;
  Dwarf_Addr lowpc;
  if ((dwarf_tag(die, &tag, nullptr) != DW_DLV_OK) ||
      (tag != DW_TAG_GNU_call_site) ||
      (dwarf_lowpc(die, &lowpc, nullptr) != DW_DLV_OK)) {
    return;
  }

  die_ptr origin = this->die_origin(die);
  if (origin != nullptr) {
    char *name;
    if (dwarf_diename(origin.get(), &name, nullptr) == DW_DLV_OK) {
      str_ptr str_wrap(name, dwarf_deleter(dbg));
      table.emplace_back(callsite_t(lowpc, name));
      return;
    }
  }
  table.emplace_back(callsite_t(lowpc));
}

void dwarf_t::die_subprogram(Dwarf_Die die, subroutine_map &table) {
  Dwarf_Half tag;
  if ((dwarf_tag(die, &tag, nullptr) != DW_DLV_OK) ||
      (tag != DW_TAG_subprogram)) {
    return;
  }

  Dwarf_Addr lowpc, highpc;
  Dwarf_Half form;
  enum Dwarf_Form_Class form_class;
  str_ptr name = this->die_name(die);
  if ((name == nullptr) || (dwarf_lowpc(die, &lowpc, nullptr) != DW_DLV_OK) ||
      (dwarf_highpc_b(die, &highpc, &form, &form_class, nullptr) !=
       DW_DLV_OK)) {
    return;
  }

  // DWARF4: DW_AT_high_pc serves as an offset from DW_AT_low_pc
  if (form_class == DW_FORM_CLASS_CONSTANT) {
    highpc += lowpc;
  }

  subroutine_map::iterator iter;
  bool placed;
  std::tie(iter, placed) =
      table.emplace(lowpc, subroutine_t(name.get(), highpc, true));
  if (!placed) {
    return;
  }

  // Enumerate call sites
  if (dwarf_child(die, &die, nullptr) == DW_DLV_OK) {
    die_ptr die_wrap(die, dwarf_deleter(dbg));
    this->siblings(
        std::move(die_wrap), &dwarf_t::die_callsite, iter->second.callsites);
  }
}

dwarf_t::die_ptr dwarf_t::die_origin(Dwarf_Die die) {
  Dwarf_Attribute attr;
  Dwarf_Off offset;

  if (dwarf_attr(die, DW_AT_abstract_origin, &attr, nullptr) == DW_DLV_OK) {
    attr_ptr attr_wrap(attr, dwarf_deleter(dbg));

    if ((dwarf_global_formref(attr, &offset, nullptr) != DW_DLV_OK) ||
        (dwarf_offdie_b(this->dbg, offset, 1, &die, nullptr) != DW_DLV_OK)) {
      die = nullptr;
    }
  } else {
    die = nullptr;
  }
  return die_ptr(die, dwarf_deleter(dbg));
}

dwarf_t::str_ptr dwarf_t::die_name(Dwarf_Die die) {
  char *str;
  if (dwarf_diename(die, &str, nullptr) != DW_DLV_OK) {
    // Inspect abstract origin DIE when DW_AT_name is absent
    die_ptr origin = this->die_origin(die);
    if ((origin == nullptr) ||
        (dwarf_diename(origin.get(), &str, nullptr) != DW_DLV_OK)) {
      str = nullptr;
    }
  }
  return str_ptr(str, dwarf_deleter(dbg));
}

void subroutine_t::print(uint64_t pc_low) const {
  const char *label = this->name.c_str();
  if (this->pc_end) {
    printf("%08" PRIx64 " %08" PRIx64 " <%s>\n", pc_low, this->pc_end, label);
  } else {
    printf("%08" PRIx64 " - <%s>\n", pc_low, label);
  }
  for (const callsite_t &site : this->callsites) {
    if (site.name.empty()) {
      printf("\t%08" PRIx64 "\n", site.pc);
    } else {
      printf("\t%08" PRIx64 " <%s>\n", site.pc, site.name.c_str());
    }
  }
}

const subroutine_t *subroutine_find(const subroutine_map &table, uint64_t pc) {
  subroutine_map::const_iterator iter = table.upper_bound(pc);
  if (iter == table.begin()) {
    return nullptr;
  }
  --iter;
  const subroutine_t &func = iter->second;
  if ((func.pc_end == 0) || (pc < func.pc_end)) {
    return &func;
  }
  return nullptr;
}
