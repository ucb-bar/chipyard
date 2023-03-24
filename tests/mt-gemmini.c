#include "encoding.h"
#include <stdio.h>
#include "../generators/gemmini/software/gemmini-rocc-tests/include/gemmini.h"

int main(void) {
  __main();
  return 0;
}

volatile static int go_hart = 0;

void __main(void) {
  size_t mhartid = read_csr(mhartid);
  #define MAT_DIM_I 32
  #define MAT_DIM_J 32
  #define MAT_DIM_K 4
  #define NO_BIAS 1
  #define FULL_BIAS_WIDTH 1
  
  static elem_t full_A[MAT_DIM_I][MAT_DIM_K] row_align(1);
  static elem_t full_B[MAT_DIM_K][MAT_DIM_J] row_align(1);
  static elem_t full_C[MAT_DIM_I][MAT_DIM_J] row_align(1);
  static acc_t full_D[MAT_DIM_I][MAT_DIM_J] row_align_acc(1);
  
  static elem_t gold[MAT_DIM_I][MAT_DIM_J];

  size_t start_cycle = rdcycle();

  tiled_matmul_auto(MAT_DIM_I, MAT_DIM_J, MAT_DIM_K,
                    (elem_t*)full_A, (elem_t*)full_B, NO_BIAS ? NULL : &full_D[0][0], (elem_t*)full_C,
                    MAT_DIM_K, MAT_DIM_J, MAT_DIM_J, MAT_DIM_J,
                    MVIN_SCALE_IDENTITY, MVIN_SCALE_IDENTITY, MVIN_SCALE_IDENTITY,
                    NO_ACTIVATION, ACC_SCALE_IDENTITY, 0, false,
                    false, false,
                    false, !FULL_BIAS_WIDTH,
                    0,
                    WS);
  size_t end_cycle = rdcycle();

  size_t cycles = end_cycle - start_cycle;

  while (go_hart != mhartid);
  printf("%d: %ld\n", mhartid, cycles);
  go_hart++;
  while (1);
}
