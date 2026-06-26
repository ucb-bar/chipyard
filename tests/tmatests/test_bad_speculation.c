// Bad Speculation Test — verifies mispredictions cause bad_speculation slots
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

static volatile uint64_t lfsr_state = 0xACE1u;

static int unpredictable_branch(void) {
  uint64_t bit = ((lfsr_state >> 0) ^ (lfsr_state >> 2) ^
                  (lfsr_state >> 3) ^ (lfsr_state >> 5)) & 1u;
  lfsr_state = (lfsr_state >> 1) | (bit << 15);
  return lfsr_state & 1;
}

int main(void) {
  volatile int sum = 0;

  // Warmup
  for (int i = 0; i < 100; i++) {
    if (unpredictable_branch()) sum++;
  }

  tma_snapshot();
  uint64_t bad_spec_before = tma_read(TMA_BAD_SPECULATION);
  uint64_t br_misp_before  = tma_read(TMA_BR_MISPREDICT);
  uint64_t br_misp_l2_before = tma_read(TMA_BRANCH_MISPREDICT);
  uint64_t mach_clr_before = tma_read(TMA_MACHINE_CLEARS);
  uint64_t br_resolve_before = tma_read(TMA_BR_RESOLVE);
  tma_release_snapshot();

  for (int i = 0; i < 2000; i++) {
    if (unpredictable_branch()) {
      sum += i;
    } else {
      sum -= i;
    }
  }
  tma_use(sum);

  tma_snapshot();
  uint64_t bad_spec_after = tma_read(TMA_BAD_SPECULATION);
  uint64_t br_misp_after  = tma_read(TMA_BR_MISPREDICT);
  uint64_t br_misp_l2_after = tma_read(TMA_BRANCH_MISPREDICT);
  uint64_t mach_clr_after = tma_read(TMA_MACHINE_CLEARS);
  uint64_t br_resolve_after = tma_read(TMA_BR_RESOLVE);
  tma_release_snapshot();

  uint64_t bad_spec_delta = bad_spec_after - bad_spec_before;
  uint64_t br_misp_delta  = br_misp_after - br_misp_before;
  uint64_t br_misp_l2_delta = br_misp_l2_after - br_misp_l2_before;
  uint64_t mach_clr_delta = mach_clr_after - mach_clr_before;
  uint64_t br_resolve_delta = br_resolve_after - br_resolve_before;

  // Unpredictable branches should cause bad speculation
  TMA_ASSERT(bad_spec_delta > 0, 1);
  TMA_ASSERT(br_misp_delta > 0, 2);
  TMA_ASSERT(br_resolve_delta > 0, 3);

  // L2 invariant: branch_mispredict_l2 + machine_clears = bad_speculation (on deltas)
  TMA_ASSERT(tma_absdiff(br_misp_l2_delta + mach_clr_delta, bad_spec_delta) <= TMA_TOLERANCE, 4);

  // Mispredicts should be a fraction of resolves (not all branches mispredict)
  TMA_ASSERT(br_misp_delta <= br_resolve_delta, 5);

  // With LFSR ~50% random, expect some mispredict rate (> 5% of resolves)
  TMA_ASSERT(br_misp_delta * 20 > br_resolve_delta, 6);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
