#include <cinttypes>
#include <cstdlib>
#include <fcntl.h>
#include <iostream>
#include <stdexcept>
#include <unistd.h>

#include "../tracerv_dwarf.h"
#include <libelf.h>

int main(int argc, char *argv[]) {
  if (argc < 2) {
    std::cerr << "usage: " << argv[0] << " <elf> [pc]" << std::endl;
    return 1;
  }

  int fd = open(argv[1], O_RDONLY);
  if (fd < 0) {
    perror("open");
    return 1;
  }

  Elf *elf;
  if ((elf_version(EV_CURRENT) == EV_NONE) ||
      ((elf = elf_begin(fd, ELF_C_READ, nullptr)) == nullptr)) {
    std::cerr << "elf_begin: " << elf_errmsg(elf_errno()) << std::endl;
    close(fd);
    return 1;
  }

  subroutine_map funcs;
  {
    dwarf_t dwarf = dwarf_t(elf);
    dwarf.subroutines(funcs);
  }

  uint64_t pc = (argc > 2) ? strtoull(argv[2], nullptr, 16) : 0;
  if (pc != 0) {
    const subroutine_t *func = subroutine_find(funcs, pc);
    if (func != nullptr) {
      std::cout << func->name << std::endl;
    } else {
      std::cerr << "No function matched for PC" << std::endl;
    }
  } else {
    for (const auto &kv : funcs) {
      kv.second.print(kv.first);
    }
  }

  elf_end(elf);
  close(fd);
  return 0;
}
