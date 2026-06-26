// Instruction Mix Test — verifies retired instruction type counters
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

static volatile uint64_t data[64];

int main(void) {
  for (int i = 0; i < 64; i++) data[i] = i;

  tma_snapshot();
  uint64_t loads_before    = tma_read(TMA_RETIRED_LOADS);
  uint64_t stores_before   = tma_read(TMA_RETIRED_STORES);
  uint64_t branches_before = tma_read(TMA_RETIRED_BRANCHES);
  uint64_t instret_before  = tma_read(TMA_INSTRET);
  tma_release_snapshot();

  volatile uint64_t sum = 0;
  for (int i = 0; i < 100; i++) {
    sum += data[i % 64];
    data[(i + 32) % 64] = sum;
  }
  tma_use(sum);

  tma_snapshot();
  uint64_t loads_after    = tma_read(TMA_RETIRED_LOADS);
  uint64_t stores_after   = tma_read(TMA_RETIRED_STORES);
  uint64_t branches_after = tma_read(TMA_RETIRED_BRANCHES);
  uint64_t instret_after  = tma_read(TMA_INSTRET);

  uint64_t ret_jals  = tma_read(TMA_RETIRED_JALS);
  uint64_t ret_jalrs = tma_read(TMA_RETIRED_JALRS);
  uint64_t ret_fp    = tma_read(TMA_RETIRED_FP);
  uint64_t ret_amo   = tma_read(TMA_RETIRED_AMO);
  uint64_t ret_sys   = tma_read(TMA_RETIRED_SYSTEM);
  uint64_t instret   = tma_read(TMA_INSTRET);
  tma_release_snapshot();

  uint64_t loads_delta    = loads_after - loads_before;
  uint64_t stores_delta   = stores_after - stores_before;
  uint64_t branches_delta = branches_after - branches_before;
  uint64_t instret_delta  = instret_after - instret_before;

  // Should have at least 100 of each type from the loop
  TMA_ASSERT(loads_delta >= 100, 1);
  TMA_ASSERT(stores_delta >= 100, 2);
  TMA_ASSERT(branches_delta >= 100, 3);

  // Sum of instruction types <= instret (invariant 9 on deltas)
  TMA_ASSERT(loads_delta + stores_delta + branches_delta <= instret_delta + TMA_TOLERANCE, 4);

  // Global invariant 9: sum of all types <= total instret
  {
    uint64_t type_sum = loads_after + stores_after + branches_after +
                        ret_jals + ret_jalrs + ret_fp + ret_amo + ret_sys;
    TMA_ASSERT(type_sum <= instret + TMA_TOLERANCE, 5);
  }

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
