#include "encoding.h"
#include <stdio.h>

const char* get_march(size_t marchid) {
  switch (marchid) {
  case 1:
    return "rocket";
  case 2:
    return "sonicboom";
  case 5:
    return "spike";
  default:
    return "unknown";
  }
}

volatile static int go_hart = 0;

void __main(void) {
  size_t mhartid = read_csr(mhartid);
  size_t marchid = read_csr(marchid);
  const char* march = get_march(marchid);

  while (go_hart != mhartid);
  printf("%ld %s\n", mhartid, march);
  go_hart++;

  while (1);
}

int main(void) {
  __main();
}
