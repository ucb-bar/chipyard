// Memory Bound Test — verifies pointer chasing causes dcache misses and memory_bound
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

#define ARRAY_SIZE (16 * 1024 / sizeof(uint64_t))
static volatile uint64_t array[ARRAY_SIZE];

static void init_pointer_chase(void) {
  uint64_t stride = 64;
  uint64_t idx = 0;
  for (uint64_t i = 0; i < ARRAY_SIZE; i++) {
    uint64_t next = (idx + stride) % ARRAY_SIZE;
    array[idx] = next;
    idx = next;
  }
}

int main(void) {
  init_pointer_chase();

  volatile uint64_t idx = 0;
  for (int i = 0; i < 100; i++) idx = array[idx];

  tma_snapshot();
  uint64_t dc_before  = tma_read(TMA_DCACHE_MISS);
  uint64_t be_before  = tma_read(TMA_BACKEND_BOUND);
  uint64_t mem_before = tma_read(TMA_MEMORY_BOUND);
  uint64_t core_before = tma_read(TMA_CORE_BOUND);
  uint64_t loads_before = tma_read(TMA_RETIRED_LOADS);
  tma_release_snapshot();

  for (int i = 0; i < 500; i++) {
    idx = array[idx];
  }
  tma_use(idx);

  tma_snapshot();
  uint64_t dc_after  = tma_read(TMA_DCACHE_MISS);
  uint64_t be_after  = tma_read(TMA_BACKEND_BOUND);
  uint64_t mem_after = tma_read(TMA_MEMORY_BOUND);
  uint64_t core_after = tma_read(TMA_CORE_BOUND);
  uint64_t loads_after = tma_read(TMA_RETIRED_LOADS);
  tma_release_snapshot();

  uint64_t be_delta  = be_after - be_before;
  uint64_t mem_delta = mem_after - mem_before;
  uint64_t core_delta = core_after - core_before;

  // Pointer chasing should cause dcache misses
  TMA_ASSERT(dc_after - dc_before > 0, 1);
  TMA_ASSERT(be_delta > 0, 2);

  // Should retire loads (pointer chase = loads)
  TMA_ASSERT(loads_after - loads_before >= 500, 3);

  // L2 invariant: memory_bound + core_bound = backend_bound (on deltas)
  TMA_ASSERT(tma_absdiff(mem_delta + core_delta, be_delta) <= TMA_TOLERANCE, 4);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
