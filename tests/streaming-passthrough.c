#define PASSTHROUGH_WRITE 0x2200
#define PASSTHROUGH_WRITE_COUNT 0x2208
#define PASSTHROUGH_READ 0x2300
#define PASSTHROUGH_READ_COUNT 0x2308

#include "mmio.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

int main(void)
{
  printf("Starting writing\n");
  uint32_t test_vector[7] = {3, 2, 1, 0, -1, -2, -3} ;
  for (int i = 0; i < 7; i++) {
    reg_write64(PASSTHROUGH_WRITE, test_vector[i]);
  }

  printf("Done writing\n");
  uint32_t rcnt = reg_read32(PASSTHROUGH_READ_COUNT);
  printf("Write count: %d\n", reg_read32(PASSTHROUGH_WRITE_COUNT));
  printf("Read count: %d\n", rcnt);

  int failed = 0;
  if (rcnt != 0) {
    for (int i = 0; i < 7; i++) {
      uint32_t res = reg_read32(PASSTHROUGH_READ);
      uint32_t expected = test_vector[i];
      if (res == expected) {
        printf("\n\nPass: Got %d Expected %d\n\n", res, test_vector[i]);
      } else {
        failed = 1;
        printf("\n\nFail: Got %d Expected %d\n\n", res, test_vector[i]);
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
