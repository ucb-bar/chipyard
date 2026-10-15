#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <riscv-pk/encoding.h>
#include "marchid.h"
#include "router.h"

//   IO hub is chip id 1 (port 0 -> compute id 2, port 1 -> compute id 3);
//   the two compute chiplets are chip ids 2 and 3.
//
//   Build the binary with `cmake -S ./ -B ./build/ && cmake --build ./build/`, then
//   from a sim dir (e.g. sims/verilator) run:
//   make run-binary CONFIG=TripleChipletConfig BINARY=../../tests/build/triple-chiplet.riscv \
//     EXTRA_SIM_FLAGS="+chip_id0=0x00004080:0x00000001 +chip_id1=0x00004080:0x00000002 +chip_id2=0x00004080:0x00000003"

#define OFFCHIP_OFFSET   0x100000000L
#define COMPUTE2_OFFSET  (OFFCHIP_OFFSET * 2)  // reach chip id 2
#define COMPUTE3_OFFSET  (OFFCHIP_OFFSET * 3)  // reach chip id 3

uint32_t src[10] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
uint32_t dest[10];
uint32_t test[10];

int rw_mem(uint64_t offset) {
  size_t write_start = rdcycle();

  uint32_t* offchip_addr = (uint32_t*)((uintptr_t)dest + offset);

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
      printf("Remote write/read failed at index %d %p %p %p %x %x\n", i, src+i, test+i, dest + offset + i, src[i], test[i]);
      exit(1);
    }
  }

  printf("Read %ld bytes in %ld cycles\n", sizeof(src), read_end - read_start);

  return 0;
}

int main(void) {

  int chip_id = reg_read64(CHIP_ID_ADDR);

  printf("Got chip ID: %d\n", chip_id);

  if (chip_id == 1) {
    // IO hub: forward each compute chiplet's traffic out the port it sits on.
    program_router(0, 2, 0);  // dest chip id 2 -> port 0 (toward compute id 2)
    program_router(1, 3, 1);  // dest chip id 3 -> port 1 (toward compute id 3)
    printf("Chip id 1 (IO hub) DONE\n");
  } else if (chip_id == 2) {
    program_router(0, 3, 0);  // dest chip id 3 -> port 0 (toward IO hub)
    rw_mem(COMPUTE3_OFFSET);
    printf("Chip id 2 DONE\n");
  } else if (chip_id == 3) {
    program_router(0, 2, 0);  // dest chip id 2 -> port 0 (toward IO hub)
    rw_mem(COMPUTE2_OFFSET);
    printf("Chip id 3 DONE\n");
  } else {
    printf("Invalid chip ID: %d\n", chip_id);
    exit(1);
  }

  return 0;
}
