#include <stdio.h>
#include <riscv-pk/encoding.h>
#include "calyx-sum.h"

#define TEST_SIZE 3
int main() {

  int test_inputs[TEST_SIZE] = {1, 2, 3};

  for (int i = 0; i < TEST_SIZE; i++) {
    calyx_sum_send_input(test_inputs[i]);
    int out = calyx_sum_get_output();
    int expect = test_inputs[i] * 3;
    if (out != expect) {
      printf("expect %d got %d\n", expect, out);
      return 1;
    }
  }
  printf("[*] Test success!\n");
  return 0;
}
