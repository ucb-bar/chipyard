// Stall Reasons Test — verifies dispatch stall counters fire under load
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

static volatile uint64_t big_array[256];

int main(void) {
  for (int i = 0; i < 256; i++) big_array[i] = i;

  tma_snapshot();
  uint64_t rob_before = tma_read(TMA_ROB_FULL);
  uint64_t ldq_before = tma_read(TMA_LDQ_FULL);
  uint64_t stq_before = tma_read(TMA_STQ_FULL);
  uint64_t ren_before = tma_read(TMA_RENAME_STALL);
  uint64_t be_before  = tma_read(TMA_BACKEND_BOUND);
  uint64_t mem_before = tma_read(TMA_MEMORY_BOUND);
  uint64_t core_before = tma_read(TMA_CORE_BOUND);
  tma_release_snapshot();

  volatile uint64_t sum = 0;
  for (int i = 0; i < 200; i++) {
    sum += big_array[i % 256];
    sum += big_array[(i * 7) % 256];
    sum += big_array[(i * 13) % 256];
    sum += big_array[(i * 31) % 256];
  }

  for (int i = 0; i < 200; i++) {
    big_array[i % 256] = sum + i;
    big_array[(i * 7) % 256] = sum + i + 1;
    big_array[(i * 13) % 256] = sum + i + 2;
    big_array[(i * 31) % 256] = sum + i + 3;
  }
  tma_use(sum);

  tma_snapshot();
  uint64_t rob_after = tma_read(TMA_ROB_FULL);
  uint64_t ldq_after = tma_read(TMA_LDQ_FULL);
  uint64_t stq_after = tma_read(TMA_STQ_FULL);
  uint64_t ren_after = tma_read(TMA_RENAME_STALL);
  uint64_t be_after  = tma_read(TMA_BACKEND_BOUND);
  uint64_t mem_after = tma_read(TMA_MEMORY_BOUND);
  uint64_t core_after = tma_read(TMA_CORE_BOUND);
  tma_release_snapshot();

  uint64_t be_delta  = be_after - be_before;
  uint64_t mem_delta = mem_after - mem_before;
  uint64_t core_delta = core_after - core_before;

  // Backend bound should fire
  TMA_ASSERT(be_delta > 0, 1);

  // At least one specific stall reason should fire
  uint64_t any_stall = (rob_after - rob_before) + (ldq_after - ldq_before) +
                       (stq_after - stq_before) + (ren_after - ren_before);
  TMA_ASSERT(any_stall > 0, 2);

  // L2 invariant: memory_bound + core_bound = backend_bound
  TMA_ASSERT(tma_absdiff(mem_delta + core_delta, be_delta) <= TMA_TOLERANCE, 3);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
