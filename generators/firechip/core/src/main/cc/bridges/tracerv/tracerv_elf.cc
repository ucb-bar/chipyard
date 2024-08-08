#include "tracerv_elf.h"

#include <cinttypes>
#include <cstdio>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <utility>
#include <vector>

#include <elf.h>
#include <gelf.h>
#include <libelf.h>

namespace {
void elf_runtime_error(const char *msg) {
  std::string str(msg);
  msg = elf_errmsg(elf_errno());
  if (msg != nullptr) {
    str.append(": ");
    str.append(msg);
  }
  throw std::runtime_error(str);
}
inline void elf_version_init(void) {
  if (elf_version(EV_CURRENT) == EV_NONE) {
    elf_runtime_error("elf_version");
  }
}
} // namespace

elf_t::elf_t(int fd) {
  elf_version_init();
  this->elf = elf_begin(fd, ELF_C_READ, nullptr);
  if (this->elf == nullptr) {
    elf_runtime_error("elf_begin");
  }
}

elf_t::elf_t(char *img, size_t size) {
  elf_version_init();
  this->elf = elf_memory(img, size);
  if (this->elf == nullptr) {
    elf_runtime_error("elf_memory");
  }
}

void *elf_t::section_data(const char *name, size_t *size) {
  size_t shstrndx;
  if (elf_getshdrstrndx(this->elf, &shstrndx) != 0) {
    elf_runtime_error("elf_getshdrstrndx");
  }

  Elf_Scn *scn = nullptr;
  while ((scn = elf_nextscn(this->elf, scn)) != nullptr) {
    GElf_Shdr shdr;
    if (gelf_getshdr(scn, &shdr) == nullptr) {
      elf_runtime_error("gelf_getshdr");
    }
    char *shname = elf_strptr(this->elf, shstrndx, shdr.sh_name);
    if (shname == nullptr) {
      elf_runtime_error("elf_strptr");
    }
    if (strcmp(name, shname) == 0) {
      Elf_Data *data = elf_getdata(scn, nullptr);
      if (data == nullptr) {
        elf_runtime_error("elf_getdata");
      }
      *size = data->d_size;
      return data->d_buf;
    }
  }
  return nullptr;
}

std::pair<uint64_t, uint64_t> elf_t::subroutines(subroutine_map &table) {
  {
    dwarf_t dwarf(this->elf);
    dwarf.subroutines(table);
  }

  size_t shnum;
  if (elf_getshdrnum(this->elf, &shnum) != 0) {
    elf_runtime_error("elf_getshdrnum");
  }

  uint64_t lowpc = UINT64_MAX;
  uint64_t highpc = 0;

  std::vector<bool> text(shnum);
  Elf_Scn *stscn = nullptr;
  int stnum = 0;

  Elf_Scn *scn = nullptr;
  for (size_t shndx = 1; shndx < shnum; shndx++) {
    GElf_Shdr shdr;
    if ((scn = elf_nextscn(this->elf, scn)) == nullptr) {
      elf_runtime_error("elf_nextscn");
    }
    if (gelf_getshdr(scn, &shdr) == nullptr) {
      elf_runtime_error("elf_getshdr");
    }
    if ((text[shndx] = (shdr.sh_flags & SHF_EXECINSTR))) {
      // Record lowest/highest addresses of executable sections
      uint64_t pc = shdr.sh_addr;
      if (pc < lowpc) {
        lowpc = pc;
      }
      pc += shdr.sh_size;
      if (pc > highpc) {
        highpc = pc;
      }
    }
    if (shdr.sh_type == SHT_SYMTAB) {
      if (stnum == 0) {
        stscn = scn;
      }
      stnum++;
    }
  }

  for (scn = stscn; stnum > 0; stnum--) {
    GElf_Shdr shdr;
    for (;;) {
      if (gelf_getshdr(scn, &shdr) == nullptr) {
        elf_runtime_error("gelf_getshdr");
      }
      if (shdr.sh_type == SHT_SYMTAB) {
        break;
      }
      if ((scn = elf_nextscn(this->elf, scn)) == nullptr) {
        elf_runtime_error("elf_nextscn");
      }
    }

    Elf_Data *data = elf_getdata(scn, nullptr);
    if (data == nullptr) {
      elf_runtime_error("elf_getdata");
    }

    int size = shdr.sh_size / shdr.sh_entsize;
    for (int ndx = 0; ndx < size; ndx++) {
      GElf_Sym sym;
      if (gelf_getsym(data, ndx, &sym) == nullptr) {
        elf_runtime_error("gelf_getsym");
      }
      if ((sym.st_shndx < 1) || (sym.st_shndx >= shnum) ||
          !text[sym.st_shndx]) {
        continue;
      }

      auto iter = table.upper_bound(sym.st_value);
      if (iter != table.begin()) {
        auto prev = std::prev(iter);
        // Skip if address corresponds to a previously defined
        // symbol or falls within an existing subroutine range
        if ((sym.st_value == prev->first) ||
            (sym.st_value < prev->second.pc_end)) {
          continue;
        }
      }

      char *name = elf_strptr(this->elf, shdr.sh_link, sym.st_name);
      if ((name != nullptr) && (name[0] != '\0')) {
        table.emplace_hint(
            iter,
            sym.st_value,
            subroutine_t(name, 0, (GELF_ST_TYPE(sym.st_info) == STT_FUNC)));
      }
    }
  }
  return std::make_pair(lowpc, highpc);
}
