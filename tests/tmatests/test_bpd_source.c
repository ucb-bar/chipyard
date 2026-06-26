// Branch Predictor Source Test — verifies branch prediction inequality invariants
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  volatile int sum = 0;
  for (int i = 0; i < 1000; i++) sum += 1;

  tma_snapshot();
  uint64_t br_resolve_before = tma_read(TMA_BR_RESOLVE);
  uint64_t br_misp_before    = tma_read(TMA_BR_MISPREDICT);
  uint64_t jalr_misp_before  = tma_read(TMA_JALR_MISPREDICT);
  tma_release_snapshot();

  for (int i = 0; i < 2000; i++) {
    sum += 1;
  }
  tma_use(sum);

  tma_snapshot();
  uint64_t br_resolve_after = tma_read(TMA_BR_RESOLVE);
  uint64_t br_misp_after    = tma_read(TMA_BR_MISPREDICT);
  uint64_t jalr_misp_after  = tma_read(TMA_JALR_MISPREDICT);
  uint64_t misp_bpd  = tma_read(TMA_BR_MISPRED_BPD);
  uint64_t misp_btb  = tma_read(TMA_BR_MISPRED_BTB);
  uint64_t br_misp_total = tma_read(TMA_BR_MISPREDICT);
  tma_release_snapshot();

  uint64_t resolve_delta = br_resolve_after - br_resolve_before;
  uint64_t misp_delta    = br_misp_after - br_misp_before;

  // Branches should resolve (loop has branch each iteration)
  TMA_ASSERT(resolve_delta > 0, 1);

  // Most branches predicted correctly in a simple loop
  TMA_ASSERT(resolve_delta > misp_delta, 2);

  // Inequality invariants on totals:
  // jalr_mispredict <= br_mispredict
  TMA_ASSERT(jalr_misp_after <= br_misp_total + TMA_TOLERANCE, 3);

  // br_mispredict <= br_resolve
  TMA_ASSERT(br_misp_total <= br_resolve_after + TMA_TOLERANCE, 4);

  // br_mispred_bpd + br_mispred_btb <= br_mispredict
  TMA_ASSERT(misp_bpd + misp_btb <= br_misp_total + TMA_TOLERANCE, 5);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
