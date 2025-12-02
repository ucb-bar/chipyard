#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <riscv-pk/encoding.h>
#include "marchid.h"

#define CHIP_OFFSET (0x10L << 32)

uint32_t src[10] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
uint32_t dest[10];
uint32_t test[10];

int main(void) {
  size_t write_start = rdcycle();

  uint32_t* offchip_addr = (uint32_t*)((uintptr_t)dest + CHIP_OFFSET);

  // Using inline ASM because CTC requires 32b transactions
  for (int i = 0; i < 10; i++) {
      asm volatile(
          "lw  t0, 0(%1)\n"
          "sw  t0, 0(%0)\n"
          :
          : "r"(offchip_addr + i),
            "r"(src + i)
          : "t0", "memory"
      );
  }

  size_t write_end = rdcycle();

  printf("Wrote %ld bytes in %ld cycles\n", sizeof(src), write_end - write_start);

  size_t read_start = rdcycle();

  for (int i = 0; i < 10; i++) {
    asm volatile(
        "lw  t0, 0(%0)\n"
        "sw  t0, 0(%1)\n"
        :
        : "r"(offchip_addr + i),
          "r"(test + i)
        : "t0", "memory"
    );
  }
  
  size_t read_end = rdcycle();

  for (int i = 0; i < sizeof(src) / 4; i++) {
    if (src[i] != test[i]) {
      printf("Remote write/read failed at index %d %p %p %p %x %x\n", i, src+i, test+i, dest + CHIP_OFFSET + i, src[i], test[i]);
      exit(1);
    }
  }

  printf("Read %ld bytes in %ld cycles\n", sizeof(src), read_end - read_start);

  return 0;
}
