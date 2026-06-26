// Backend Bound Test — verifies data-dependent divisions cause backend stalls
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  volatile uint64_t result = 1;
  for (int i = 0; i < 100; i++) result += i;

  tma_snapshot();
  uint64_t be_before   = tma_read(TMA_BACKEND_BOUND);
  uint64_t core_before = tma_read(TMA_CORE_BOUND);
  uint64_t mem_before  = tma_read(TMA_MEMORY_BOUND);
  tma_release_snapshot();

  uint64_t a = result;
  for (int i = 0; i < 500; i++) {
    a = a / 3 + 1;
    a = a / 5 + 1;
    a = a / 7 + 1;
  }
  result = a;
  tma_use(result);

  tma_snapshot();
  uint64_t be_after   = tma_read(TMA_BACKEND_BOUND);
  uint64_t core_after = tma_read(TMA_CORE_BOUND);
  uint64_t mem_after  = tma_read(TMA_MEMORY_BOUND);
  tma_release_snapshot();

  uint64_t be_delta   = be_after - be_before;
  uint64_t core_delta = core_after - core_before;
  uint64_t mem_delta  = mem_after - mem_before;

  // Backend bound should increase significantly with dependent divides
  TMA_ASSERT(be_delta > 0, 1);
  TMA_ASSERT(core_delta > 0, 2);

  // L2 invariant: memory_bound + core_bound = backend_bound (on deltas)
  TMA_ASSERT(tma_absdiff(mem_delta + core_delta, be_delta) <= TMA_TOLERANCE, 3);

  // Division workload is compute-bound, so core_bound should dominate memory_bound
  TMA_ASSERT(core_delta > mem_delta, 4);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
