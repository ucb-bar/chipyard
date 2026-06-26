// No-op test — verifies invariants hold even with no explicit workload
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
