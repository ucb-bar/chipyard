// MMIO Counter Read Test — validates snapshot consistency and counter advancement
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  // Test 1: cycles should be non-zero
  tma_snapshot();
  uint64_t cycles = tma_read(TMA_CYCLES);
  uint64_t instret = tma_read(TMA_INSTRET);
  tma_release_snapshot();

  TMA_ASSERT(cycles > 0, 1);
  TMA_ASSERT(instret > 0, 2);

  // Test 2: snapshot consistency — two reads of same counter should match
  tma_snapshot();
  uint64_t c1 = tma_read(TMA_CYCLES);
  uint64_t c2 = tma_read(TMA_CYCLES);
  tma_release_snapshot();

  TMA_ASSERT(c1 == c2, 3);

  // Test 3: counters advance after work
  tma_snapshot();
  uint64_t before = tma_read(TMA_CYCLES);
  tma_release_snapshot();

  volatile int sum = 0;
  for (int i = 0; i < 1000; i++) sum += i;
  tma_use(sum);

  tma_snapshot();
  uint64_t after = tma_read(TMA_CYCLES);
  tma_release_snapshot();

  TMA_ASSERT(after > before, 4);

  // Test 4: all 40 counters are readable (no bus errors)
  tma_snapshot();
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    volatile uint64_t val = tma_read(0x008 + i * 0x008);
    (void)val;
  }
  tma_release_snapshot();

  // Test 5: snapshot of all L2 counters consistent with L1
  tma_snapshot();
  uint64_t retiring  = tma_read(TMA_RETIRING);
  uint64_t bad_spec  = tma_read(TMA_BAD_SPECULATION);
  uint64_t fe_bound  = tma_read(TMA_FRONTEND_BOUND);
  uint64_t be_bound  = tma_read(TMA_BACKEND_BOUND);
  uint64_t fetch_lat = tma_read(TMA_FETCH_LATENCY);
  uint64_t fetch_bw  = tma_read(TMA_FETCH_BANDWIDTH);
  uint64_t br_misp   = tma_read(TMA_BRANCH_MISPREDICT);
  uint64_t mach_clr  = tma_read(TMA_MACHINE_CLEARS);
  uint64_t mem_bound = tma_read(TMA_MEMORY_BOUND);
  uint64_t core_bnd  = tma_read(TMA_CORE_BOUND);
  tma_release_snapshot();

  uint64_t total = retiring + bad_spec + fe_bound + be_bound;
  TMA_ASSERT(total > 0, 5);

  // Verify L2 subdivisions within snapshot
  TMA_ASSERT(tma_absdiff(fetch_lat + fetch_bw, fe_bound) <= TMA_TOLERANCE, 6);
  TMA_ASSERT(tma_absdiff(br_misp + mach_clr, bad_spec) <= TMA_TOLERANCE, 7);
  TMA_ASSERT(tma_absdiff(mem_bound + core_bnd, be_bound) <= TMA_TOLERANCE, 8);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
