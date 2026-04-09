#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <riscv-pk/encoding.h>
#include "marchid.h"
#include "router.h"

#define OFFCHIP_OFFSET 0x100000000L

uint32_t src[10] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
uint32_t dest[10];
uint32_t test[10];

int rw_mem(uint64_t offset) {
  void *remote = (void *)((uint8_t *)dest + offset);

  size_t write_start = rdcycle();
  memcpy(remote, src, sizeof(src));
  size_t write_end = rdcycle();

  printf("Wrote %ld bytes in %ld cycles\n", sizeof(src), write_end - write_start);

  size_t read_start = rdcycle();
  memcpy(test, remote, sizeof(src));
  size_t read_end = rdcycle();

  for (int i = 0; i < 10; i++) {
    if (src[i] != test[i]) {
      printf("Remote write/read failed at index %d: wrote %x, read %x\n", i, src[i], test[i]);
      exit(1);
    }
  }

  printf("Read %ld bytes in %ld cycles\n", sizeof(src), read_end - read_start);

  return 0;
}

int main(void) {
  int chip_id = reg_read64(CHIP_ID_ADDR);
  printf("Got chip ID: %d\n", chip_id);
  if (chip_id != 1 && chip_id != 2) {
    printf("Invalid chip ID: %d\n", chip_id);
    exit(1);
  }

  // Enable per-port translation: port 0 strips chip ID 1, port 1 strips chip ID 2
  reg_write64(TRANSLATION_MODE_ADDR, 1);
  write_translation_table(0, 1);
  write_translation_table(1, 2);

  printf("Testing Table Translation Mode\n");

  // Route chip ID 1 → port 0, chip ID 2 → port 1
  program_router(0, 1, 0);
  program_router(1, 2, 1);

  // Test port 0: write to remote dest via chip ID 1
  rw_mem(OFFCHIP_OFFSET * 1);

  // Test port 1: write to remote dest via chip ID 2
  rw_mem(OFFCHIP_OFFSET * 2);

  printf("Testing Chip ID Translation Mode\n");
  reg_write64(TRANSLATION_MODE_ADDR, 0);

  if (chip_id == 1) {
    rw_mem(OFFCHIP_OFFSET * 2);
  } else {
    rw_mem(OFFCHIP_OFFSET * 1);
  } 

  printf("PASS\n");
  return 0;
}
