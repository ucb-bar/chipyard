#include "tracerv_processing.h"
#include "tracerv_dwarf.h"
#include "tracerv_elf.h"

#include <cinttypes>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>

ObjdumpedBinary::ObjdumpedBinary(std::string binaryWithDwarf) {
  // annotate with dwarf information
  // fn names and callsites
  int fd = open(binaryWithDwarf.c_str(), O_RDONLY);
  if (fd < 0) {
    perror("open");
    return;
  }

  subroutine_map table;
  uint64_t base, limit;
  {
    elf_t elf(fd);
    std::tie(base, limit) = elf.subroutines(table);
  }
  close(fd);

  if (!table.empty()) {
    uint64_t addr = table.begin()->first;
    this->baseaddr = (addr < base) ? addr : base;
  } else {
    this->baseaddr = base;
  }
  if (limit > this->baseaddr) {
    this->progtext.resize(limit - this->baseaddr);
  }

  size_t offset = 0;
  Instr *prev = nullptr;
  for (const auto &kv : table) {
    uint64_t pc_low = kv.first;
    const subroutine_t &sub = kv.second;

    sub.print(pc_low);

    size_t start = pc_low - this->baseaddr;
    size_t end = (sub.pc_end > pc_low) ? (sub.pc_end - this->baseaddr) : start;
    if (this->progtext.size() < end) {
      this->progtext.resize(end);
    }

    // Propagate previous unbounded label to start of current subroutine
    if (prev) {
      Instr *body = nullptr;
      for (; offset < start; offset++) {
        if (this->progtext[offset] == nullptr) {
          if (body == nullptr) {
            body = new Instr(*prev);
            body->is_fn_entry = false;
          }
          this->progtext[offset] = body;
        }
      }
    }

    // Populate subroutine entry point
    if (this->progtext[start] != nullptr) {
      fprintf(stderr,
              "subroutine overlap: %" PRIx64 " <%s>\n",
              pc_low,
              sub.name.c_str());
      continue;
    }
    Instr *entry = new Instr();
    entry->addr = pc_low; // FIXME: unused
    entry->function_name = sub.name;
    entry->is_fn_entry = true;
    entry->in_asm_sequence = !sub.function;
    this->progtext[start] = entry;

    // Populate callsites
    Instr *target = nullptr;
    for (const callsite_t &site : sub.callsites) {
      if (site.pc < pc_low) {
        fprintf(stderr,
                "callsite out of range: %" PRIx64 " <%s>\n",
                site.pc,
                sub.name.c_str());
        continue;
      }
      offset = site.pc - this->baseaddr;
      if (sub.pc_end != 0) {
        if (offset >= end) {
          fprintf(stderr,
                  "callsite out of range: %" PRIx64 " <%s>\n",
                  site.pc,
                  sub.name.c_str());
          continue;
        }
      } else if (offset >= this->progtext.size()) {
        this->progtext.resize(offset + 1);
      }

      if (this->progtext[offset] != nullptr) {
        uint64_t pc = this->baseaddr + offset;
        fprintf(stderr,
                "callsite overlap: %" PRIx64 " <%s>\n",
                pc,
                sub.name.c_str());
        continue;
      }

      if (target == nullptr) {
        target = new Instr(*entry);
        target->is_fn_entry = false;
        target->is_callsite = true;
      }
      this->progtext[offset] = target;
    }

    // Populate subroutine body
    Instr *body = nullptr;
    for (offset = start + 1; offset < end; offset++) {
      Instr *insn = this->progtext[offset];
      if (insn == nullptr) {
        if (body == nullptr) {
          body = new Instr(*entry);
          body->is_fn_entry = false;
        }
        this->progtext[offset] = body;
      } else if (insn != target) {
        uint64_t pc = this->baseaddr + offset;
        fprintf(stderr,
                "subroutine overlap: %" PRIx64 " <%s>\n",
                pc,
                sub.name.c_str());
      }
    }

    prev = sub.pc_end ? nullptr : entry;
  }
  printf("\n");

  // Propagate previous unbounded label to end of image
  if (prev) {
    Instr *body = nullptr;
    for (; offset < this->progtext.size(); offset++) {
      if (this->progtext[offset] == nullptr) {
        if (body == nullptr) {
          body = new Instr(*prev);
          body->is_fn_entry = false;
        }
        this->progtext[offset] = body;
      }
    }
  }
}

Instr *ObjdumpedBinary::getInstrFromAddr(uint64_t lookupaddress) {
  if (lookupaddress < this->baseaddr) {
    return NULL;
  }
  uint64_t computeaddr = lookupaddress - this->baseaddr;
  if (computeaddr >= this->progtext.size()) {
    return NULL;
  }
  return this->progtext[computeaddr];
}
