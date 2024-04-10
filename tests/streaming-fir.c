#define PASSTHROUGH_WRITE 0x2000
#define PASSTHROUGH_WRITE_COUNT 0x2008
#define PASSTHROUGH_READ 0x2100
#define PASSTHROUGH_READ_COUNT 0x2108

#define BP 3
#define BP_SCALE ((double)(1 << BP))

#include "mmio.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

uint64_t roundi(double x)
{
  if (x < 0.0) {
    return (uint64_t)(x - 0.5);
  } else {
    return (uint64_t)(x + 0.5);
  }
}

int main(void)
{
  double test_vector[15] = {1.0, 2.0, 3.0, 4.0, 5.0, 4.0, 3.0, 2.0, 1.0, 0.5, 0.25, 0.125, 0.125};
  uint32_t num_tests = sizeof(test_vector) / sizeof(double);
  printf("Starting writing %d inputs\n", num_tests);

  for (int i = 0; i < num_tests; i++) {
    reg_write64(PASSTHROUGH_WRITE, roundi(test_vector[i] * BP_SCALE));
  }

  printf("Done writing\n");
  uint32_t rcnt = reg_read32(PASSTHROUGH_READ_COUNT);
  printf("Write count: %d\n", reg_read32(PASSTHROUGH_WRITE_COUNT));
  printf("Read count: %d\n", rcnt);

  int failed = 0;
  if (rcnt != 0) {
    for (int i = 0; i < num_tests - 3; i++) {
      uint32_t res = reg_read32(PASSTHROUGH_READ);
      // double res = ((double)reg_read32(PASSTHROUGH_READ)) / BP_SCALE;
      double expected_double = 3*test_vector[i] + 2*test_vector[i+1] + test_vector[i+2];
      uint32_t expected = ((uint32_t)(expected_double * BP_SCALE + 0.5)) & 0xFF;
      if (res == expected) {
        printf("\n\nPass: Got %u Expected %u\n\n", res, expected);
      } else {
        failed = 1;
        printf("\n\nFail: Got %u Expected %u\n\n", res, expected);
      }
    }
  } else {
    failed = 1;
  }

  if (failed) {
    printf("\n\nSome tests failed\n\n");
  } else {
    printf("\n\nAll tests passed\n\n");
  }

  return 0;
}
