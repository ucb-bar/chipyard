// TMA Slot Sum Invariant Test — verifies all L2 subdivision equalities
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

static void nop_sled(int count) {
  for (int i = 0; i < count; i++) {
    __asm__ volatile("nop");
  }
}

int main(void) {
  nop_sled(500);

  tma_snapshot();
  uint64_t cycles    = tma_read(TMA_CYCLES);
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

  uint64_t slot_sum = retiring + bad_spec + fe_bound + be_bound;

  // L1: slot_sum should be cycles * coreWidth
  TMA_ASSERT(slot_sum > 0, 1);
  TMA_ASSERT(retiring > 0, 2);
  TMA_ASSERT(cycles > 0, 3);
  // slot_sum must be a multiple of cycles (within tolerance)
  {
    uint64_t rem = slot_sum % cycles;
    uint64_t ok = (rem <= TMA_TOLERANCE) || ((cycles - rem) <= TMA_TOLERANCE);
    TMA_ASSERT(ok, 4);
  }

  // L2 frontend: fetch_latency + fetch_bandwidth = frontend_bound
  TMA_ASSERT(tma_absdiff(fetch_lat + fetch_bw, fe_bound) <= TMA_TOLERANCE, 5);

  // L2 bad spec: branch_mispredict + machine_clears = bad_speculation
  TMA_ASSERT(tma_absdiff(br_misp + mach_clr, bad_spec) <= TMA_TOLERANCE, 6);

  // L2 backend: memory_bound + core_bound = backend_bound
  TMA_ASSERT(tma_absdiff(mem_bound + core_bnd, be_bound) <= TMA_TOLERANCE, 7);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
