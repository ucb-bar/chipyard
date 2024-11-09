#ifndef __TRACERV_ELF_H
#define __TRACERV_ELF_H

#include "tracerv_dwarf.h"
#include <cstdint>
#include <libelf.h>
#include <utility>

class elf_t {
public:
  elf_t(int);
  elf_t(char *, size_t);
  virtual ~elf_t(void) { elf_end(this->elf); }

  std::pair<uint64_t, uint64_t> subroutines(subroutine_map &);
  void *section_data(const char *, size_t *);

private:
  Elf *elf;
};

#endif // __TRACERV_ELF_H
