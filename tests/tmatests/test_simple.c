// Minimal sanity test — read MMIO counters and check basic invariants
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  tma_snapshot();
  volatile uint64_t cycles = tma_read(TMA_CYCLES);
  tma_release_snapshot();

  TMA_ASSERT(cycles > 0, 1);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
