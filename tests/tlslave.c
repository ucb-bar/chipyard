#include "mmio.h"
#include <stdio.h>
#include "tlslave.h"

int main() {
  printf("starting a random binary\n");

  for (int i = 0; i < 10; i++) {
    tlslave_write_req(i*8);
  }

  int sum = 0;
  for (int i = 0; i < 1000; i++) {
    sum += i;
  }

  return 0;
}
