#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <riscv-pk/encoding.h>
#include "marchid.h"

#define OBUS_OFFSET (0x10L << 32)

// char src[] = "This is a test string. It will be written into the off-chip memory address, then copied back.";
// char dest[4096];
// char test[4096];

uint32_t src[10] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
uint32_t dest[10];
uint32_t test[10];

int main(void) {
  size_t write_start = rdcycle();
  // memcpy(dest + OBUS_OFFSET, src, sizeof(src));
  for (int i = 0; i < sizeof(src) / 4; i++) {
    *((uint32_t *) (OBUS_OFFSET + (void *) dest + i*4)) = src[i];
  }
  size_t write_end = rdcycle();

  printf("Wrote %ld bytes in %ld cycles\n", sizeof(src), write_end - write_start);

  size_t read_start = rdcycle();
  // memcpy(test, dest + OBUS_OFFSET, sizeof(src));
  for (int i = 0; i < sizeof(src) / 4; i++) {
    test[i] = *((uint32_t *) (OBUS_OFFSET + (void *) dest + i*4));
  }
  size_t read_end = rdcycle();

  for (int i = 0; i < sizeof(src) / 4; i++) {
    if (src[i] != test[i]) {
      printf("Remote write/read failed at index %d %p %p %p %x %x\n", i, src+i, test+i, dest + OBUS_OFFSET + i, src[i], test[i]);
      exit(1);
    }
  }

  printf("Read %ld bytes in %ld cycles\n", sizeof(src), read_end - read_start);

  return 0;
}
