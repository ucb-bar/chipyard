#define PASSTHROUGH_WRITE 0x2000
#define PASSTHROUGH_WRITE_COUNT 0x2008
#define PASSTHROUGH_READ 0x2100
#define PASSTHROUGH_READ_COUNT 0x2108

#include "mmio.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

int main(void)
{
  printf("Starting writing\n");
  uint32_t num_tests = 15;
  uint32_t test_vector[15] = {1, 2, 3, 4, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1, 2};

  for (int i = 0; i < num_tests; i++) {
    reg_write64(PASSTHROUGH_WRITE, test_vector[i]);
  }

  printf("Done writing\n");
  uint32_t rcnt = reg_read32(PASSTHROUGH_READ_COUNT);
  printf("Write count: %d\n", reg_read32(PASSTHROUGH_WRITE_COUNT));
  printf("Read count: %d\n", rcnt);

  int failed = 0;
  if (rcnt != 0) {
    for (int i = 0; i < num_tests - 3; i++) {
      uint32_t res = reg_read32(PASSTHROUGH_READ);
      uint32_t expected = 3*test_vector[i] + 2*test_vector[i+1] + test_vector[i+2];
      if (res == expected) {
        printf("\n\nPass: Got %d Expected %d\n\n", res, expected);
      } else {
        failed = 1;
        printf("\n\nFail: Got %d Expected %d\n\n", res, expected);
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
