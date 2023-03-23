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

int main(void) {
  size_t mhartid = read_csr(mhartid);
  size_t marchid = read_csr(marchid);
  const char* march = get_march(marchid);
  printf("Hello from core %ld, a %s core\n", mhartid, march);
  return 0;
}
