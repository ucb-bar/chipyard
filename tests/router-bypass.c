#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include "mmio.h"

#define ROUTER_MMIO       0x4000
#define CHIP_ID_ADDR      0x4080
#define ROUTING_MODE_ADDR 0x4088
#define BYPASS_PORT_ADDR  0x4090

#define PORT0_ADDR 0x100000000L
#define PORT1_ADDR 0x200000000L

uint32_t src[10]  = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
uint32_t test[10];

void set_bypass_mode(uint8_t port) {
  reg_write64(ROUTING_MODE_ADDR, 0);   // bypass mode
  reg_write64(BYPASS_PORT_ADDR, port);
  printf("Bypass mode: port %d\n", port);
}

int rw_test(uint64_t addr) {
  volatile uint32_t *remote = (volatile uint32_t *)addr;

  for (int i = 0; i < 10; i++) {
    remote[i] = src[i];
  }
  printf("Wrote %ld bytes to 0x%lx\n", sizeof(src), addr);

  for (int i = 0; i < 10; i++) {
    test[i] = remote[i];
  }
  printf("Read %ld bytes from 0x%lx\n", sizeof(src), addr);

  for (int i = 0; i < 10; i++) {
    if (src[i] != test[i]) {
      printf("FAIL at index %d: wrote %x, read %x\n", i, src[i], test[i]);
      exit(1);
    }
  }

  return 0;
}

int main(void) {
  int chip_id = reg_read64(CHIP_ID_ADDR);
  printf("Got chip ID: %d\n", chip_id);

  // Test port 0
  set_bypass_mode(0);
  rw_test(PORT0_ADDR);

  // Test port 1
  set_bypass_mode(1);
  rw_test(PORT1_ADDR);

  printf("PASS\n");
  return 0;
}
