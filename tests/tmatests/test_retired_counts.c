// Retired Instruction Count Precision Test
// Uses inline assembly with known instruction counts to verify
// that retired_loads, retired_stores, retired_branches, retired_jals,
// and retired_fp counters track correctly.
//
// Each subtest is isolated: read before, execute asm, read after, assert, move on.
// This minimizes register pressure and compiler-generated overhead.
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

static volatile uint64_t scratch[16];

int main(void) {
  for (int i = 0; i < 16; i++) scratch[i] = i;

  // ---- Test 1: 50 loads ----
  {
    tma_snapshot();
    uint64_t before = tma_read(TMA_RETIRED_LOADS);
    tma_release_snapshot();

    __asm__ volatile(
      "ld x0,0(%0)\n" "ld x0,8(%0)\n" "ld x0,16(%0)\n" "ld x0,24(%0)\n" "ld x0,32(%0)\n"
      "ld x0,40(%0)\n" "ld x0,48(%0)\n" "ld x0,56(%0)\n" "ld x0,64(%0)\n" "ld x0,72(%0)\n"
      "ld x0,80(%0)\n" "ld x0,88(%0)\n" "ld x0,96(%0)\n" "ld x0,104(%0)\n" "ld x0,112(%0)\n"
      "ld x0,120(%0)\n" "ld x0,0(%0)\n" "ld x0,8(%0)\n" "ld x0,16(%0)\n" "ld x0,24(%0)\n"
      "ld x0,32(%0)\n" "ld x0,40(%0)\n" "ld x0,48(%0)\n" "ld x0,56(%0)\n" "ld x0,64(%0)\n"
      "ld x0,72(%0)\n" "ld x0,80(%0)\n" "ld x0,88(%0)\n" "ld x0,96(%0)\n" "ld x0,104(%0)\n"
      "ld x0,112(%0)\n" "ld x0,120(%0)\n" "ld x0,0(%0)\n" "ld x0,8(%0)\n" "ld x0,16(%0)\n"
      "ld x0,24(%0)\n" "ld x0,32(%0)\n" "ld x0,40(%0)\n" "ld x0,48(%0)\n" "ld x0,56(%0)\n"
      "ld x0,64(%0)\n" "ld x0,72(%0)\n" "ld x0,80(%0)\n" "ld x0,88(%0)\n" "ld x0,96(%0)\n"
      "ld x0,104(%0)\n" "ld x0,112(%0)\n" "ld x0,120(%0)\n" "ld x0,0(%0)\n" "ld x0,8(%0)\n"
      :: "r"(scratch) : "memory"
    );

    tma_snapshot();
    uint64_t after = tma_read(TMA_RETIRED_LOADS);
    tma_release_snapshot();
    uint64_t delta = after - before;
    TMA_ASSERT(delta >= 50, 1);
    TMA_ASSERT(delta <= 200, 2);
  }

  // ---- Test 2: 50 stores ----
  {
    tma_snapshot();
    uint64_t before = tma_read(TMA_RETIRED_STORES);
    tma_release_snapshot();

    __asm__ volatile(
      "sd x0,0(%0)\n" "sd x0,8(%0)\n" "sd x0,16(%0)\n" "sd x0,24(%0)\n" "sd x0,32(%0)\n"
      "sd x0,40(%0)\n" "sd x0,48(%0)\n" "sd x0,56(%0)\n" "sd x0,64(%0)\n" "sd x0,72(%0)\n"
      "sd x0,80(%0)\n" "sd x0,88(%0)\n" "sd x0,96(%0)\n" "sd x0,104(%0)\n" "sd x0,112(%0)\n"
      "sd x0,120(%0)\n" "sd x0,0(%0)\n" "sd x0,8(%0)\n" "sd x0,16(%0)\n" "sd x0,24(%0)\n"
      "sd x0,32(%0)\n" "sd x0,40(%0)\n" "sd x0,48(%0)\n" "sd x0,56(%0)\n" "sd x0,64(%0)\n"
      "sd x0,72(%0)\n" "sd x0,80(%0)\n" "sd x0,88(%0)\n" "sd x0,96(%0)\n" "sd x0,104(%0)\n"
      "sd x0,112(%0)\n" "sd x0,120(%0)\n" "sd x0,0(%0)\n" "sd x0,8(%0)\n" "sd x0,16(%0)\n"
      "sd x0,24(%0)\n" "sd x0,32(%0)\n" "sd x0,40(%0)\n" "sd x0,48(%0)\n" "sd x0,56(%0)\n"
      "sd x0,64(%0)\n" "sd x0,72(%0)\n" "sd x0,80(%0)\n" "sd x0,88(%0)\n" "sd x0,96(%0)\n"
      "sd x0,104(%0)\n" "sd x0,112(%0)\n" "sd x0,120(%0)\n" "sd x0,0(%0)\n" "sd x0,8(%0)\n"
      :: "r"(scratch) : "memory"
    );

    tma_snapshot();
    uint64_t after = tma_read(TMA_RETIRED_STORES);
    tma_release_snapshot();
    uint64_t delta = after - before;
    TMA_ASSERT(delta >= 50, 3);
    TMA_ASSERT(delta <= 200, 4);
  }

  // ---- Test 3: 20 branches ----
  {
    tma_snapshot();
    uint64_t before = tma_read(TMA_RETIRED_BRANCHES);
    tma_release_snapshot();

    __asm__ volatile(
      "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n"
      "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n"
      "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n"
      "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n"
      "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n" "beq x0,x0,1f\n1:\n"
      ::: "memory"
    );

    tma_snapshot();
    uint64_t after = tma_read(TMA_RETIRED_BRANCHES);
    tma_release_snapshot();
    uint64_t delta = after - before;
    TMA_ASSERT(delta >= 20, 5);
    TMA_ASSERT(delta <= 200, 6);
  }

  // ---- Test 4: 10 FP ops ----
  {
    tma_snapshot();
    uint64_t before = tma_read(TMA_RETIRED_FP);
    tma_release_snapshot();

    __asm__ volatile(
      "fadd.d f0,f0,f0\n" "fadd.d f1,f1,f1\n" "fadd.d f2,f2,f2\n" "fadd.d f3,f3,f3\n"
      "fadd.d f4,f4,f4\n" "fadd.d f5,f5,f5\n" "fadd.d f6,f6,f6\n" "fadd.d f7,f7,f7\n"
      "fadd.d f0,f0,f1\n" "fadd.d f2,f2,f3\n"
      ::: "f0","f1","f2","f3","f4","f5","f6","f7","memory"
    );

    tma_snapshot();
    uint64_t after = tma_read(TMA_RETIRED_FP);
    tma_release_snapshot();
    uint64_t delta = after - before;
    TMA_ASSERT(delta >= 10, 7);
    TMA_ASSERT(delta <= 200, 8);
  }

  // ---- Test 5: Verify sum of types <= instret and AMO = 0 ----
  {
    tma_snapshot();
    uint64_t instret = tma_read(TMA_INSTRET);
    uint64_t type_sum = tma_read(TMA_RETIRED_LOADS) + tma_read(TMA_RETIRED_STORES) +
                        tma_read(TMA_RETIRED_BRANCHES) + tma_read(TMA_RETIRED_JALS) +
                        tma_read(TMA_RETIRED_JALRS) + tma_read(TMA_RETIRED_FP) +
                        tma_read(TMA_RETIRED_AMO) + tma_read(TMA_RETIRED_SYSTEM);
    uint64_t amo = tma_read(TMA_RETIRED_AMO);
    tma_release_snapshot();

    TMA_ASSERT(type_sum <= instret + TMA_TOLERANCE, 9);
    TMA_ASSERT(type_sum * 4 > instret, 10);  // at least 25% categorized
    TMA_ASSERT(amo == 0, 11);
  }

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
