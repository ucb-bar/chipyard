#include <cinttypes>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

#include "../tracerv_elf.h"

int main(int argc, char *argv[]) {
  int fd = open(argv[1], O_RDONLY);
  if (fd < 0) {
    perror("open");
    return 1;
  }

  subroutine_map table;
  {
    elf_t elf(fd);
    elf.subroutines(table);
  }
  close(fd);
  for (const auto &kv : table) {
    kv.second.print(kv.first);
  }

  return 0;
}
