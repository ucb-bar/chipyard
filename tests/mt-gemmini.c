#include "encoding.h"
#include <stdio.h>
#include "../generators/gemmini/software/gemmini-rocc-tests/include/gemmini.h"

const char* get_march(size_t marchid) {
  switch (marchid) {
  case 1:
    return "rocket";
  case 2:
    return "sonicboom";
  case 5:
    return "spike";
  default:
    return "unknown";
  }
}

volatile static int go_hart = 0;

void __main(void) {
  size_t mhartid = read_csr(mhartid);
  size_t marchid = read_csr(marchid);
  const char* march = get_march(marchid);
  #define MAT_DIM_I 16
  #define MAT_DIM_J 16
  #define MAT_DIM_K 4
  #define NO_BIAS 1
  #define FULL_BIAS_WIDTH 1

  static elem_t full_A[MAT_DIM_I][MAT_DIM_K] row_align(1);
  static elem_t full_B[MAT_DIM_K][MAT_DIM_J] row_align(1);
  static elem_t full_C[MAT_DIM_I][MAT_DIM_J] row_align(1);
  static acc_t full_D[MAT_DIM_I][MAT_DIM_J] row_align_acc(1);
  size_t start_cycle = rdcycle();
  size_t start_insns = rdinstret();

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
  size_t end_insns = rdinstret();

  size_t cpu_start_cycle = rdcycle();
  size_t cpu_start_insns = rdinstret();

  tiled_matmul_auto(MAT_DIM_I, MAT_DIM_J, MAT_DIM_K,
                    (elem_t*)full_A, (elem_t*)full_B, NO_BIAS ? NULL : &full_D[0][0], (elem_t*)full_C,
                    MAT_DIM_K, MAT_DIM_J, MAT_DIM_J, MAT_DIM_J,
                    MVIN_SCALE_IDENTITY, MVIN_SCALE_IDENTITY, MVIN_SCALE_IDENTITY,
                    NO_ACTIVATION, ACC_SCALE_IDENTITY, 0, false,
                    false, false,
                    false, !FULL_BIAS_WIDTH,
                    0,
                    CPU);
  size_t cpu_end_cycle = rdcycle();
  size_t cpu_end_insns = rdinstret();


  size_t cycles = end_cycle - start_cycle;
  size_t cpu_cycles = cpu_end_cycle - cpu_start_cycle;
  size_t insns = end_insns - start_insns;
  size_t cpu_insns = cpu_end_insns - cpu_start_insns;
  
  while (go_hart != mhartid);

  printf("%s%d\n", march, mhartid);
  printf(" G: %ld cyc %ld insns\n", cycles, insns);
  printf(" C: %ld cyc %ld insns\n", cpu_cycles, cpu_insns);
  go_hart++;
  while (1);
}


int main(void) {
  __main();
  return 0;
}
