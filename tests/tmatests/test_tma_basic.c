// Basic TMA counter sanity test with IPC and nonzero checks
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  volatile int sum = 0;
  for (int i = 0; i < 5000; i++) sum += i;
  tma_use(sum);

  tma_snapshot();
  uint64_t cycles   = tma_read(TMA_CYCLES);
  uint64_t instret  = tma_read(TMA_INSTRET);
  uint64_t retiring = tma_read(TMA_RETIRING);
  uint64_t branches = tma_read(TMA_RETIRED_BRANCHES);
  tma_release_snapshot();

  // Basic nonzero checks
  TMA_ASSERT(cycles > 0, 1);
  TMA_ASSERT(instret > 0, 2);
  TMA_ASSERT(retiring > 0, 3);
  TMA_ASSERT(branches > 0, 4);

  // IPC sanity: should be between 0.1 and 8 (max coreWidth)
  // Check instret * 10 > cycles (IPC > 0.1) and instret < cycles * 8
  TMA_ASSERT(instret * 10 > cycles, 5);
  TMA_ASSERT(instret < cycles * 8, 6);

  // retiring should be >= instret (slots >= instructions)
  TMA_ASSERT(retiring >= instret, 7);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
