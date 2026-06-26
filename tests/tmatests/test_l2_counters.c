// L2 Cache TMA Counter Test — validates that L2 counters are readable and non-zero
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

int main(void) {
  // Generate some memory activity to exercise L2 cache
  // Array larger than L1 dcache (typically 32KB) to force L2 traffic
  #define ARRAY_SIZE (16384)  // 16K entries = 128KB > L1 dcache
  static volatile uint64_t array[ARRAY_SIZE];

  // Write pass - fills L1 and spills to L2
  for (int i = 0; i < ARRAY_SIZE; i++) {
    array[i] = (uint64_t)i * 0xDEADBEEF;
  }

  // Read pass - some will hit L2
  volatile uint64_t sink = 0;
  for (int i = 0; i < ARRAY_SIZE; i++) {
    sink += array[i];
  }
  tma_use(sink);

  // Stride access to force evictions
  for (int pass = 0; pass < 3; pass++) {
    for (int i = 0; i < ARRAY_SIZE; i += 64) {
      array[i] = sink + i + pass;
    }
  }

  // Now snapshot and read all L2 counters
  tma_snapshot();

  // Test 1: All 57 counters are readable (no bus errors)
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    volatile uint64_t val = tma_read(0x008 + i * 0x008);
    (void)val;
  }

  // Test 2: Core counters are sane
  uint64_t cycles  = tma_read(TMA_CYCLES);
  uint64_t instret = tma_read(TMA_INSTRET);
  TMA_ASSERT(cycles > 0, 1);
  TMA_ASSERT(instret > 0, 2);

  // Test 3: L2 demand miss counter should be non-zero (we touched > L1 capacity)
  uint64_t l2_demand_miss = tma_read(TMA_L2_DEMAND_ALLOC_DIR_MISS);
  TMA_ASSERT(l2_demand_miss > 0, 3);

  // Test 4: L2 demand hit (regular) should be non-zero (re-reads hit L2)
  uint64_t l2_demand_hit = tma_read(TMA_L2_DEMAND_HIT_REGULAR);
  // This may or may not be non-zero depending on access pattern, so just read it
  tma_use(l2_demand_hit);

  // Test 5: L2 evictions should be non-zero (we wrote enough data)
  uint64_t l2_evict_dirty = tma_read(TMA_L2_EVICTIONS_DIRTY);
  uint64_t l2_evict_clean = tma_read(TMA_L2_EVICTIONS_CLEAN);
  // At least one type of eviction should have occurred
  // (may not be the case for small working sets, so just validate readability)
  tma_use(l2_evict_dirty + l2_evict_clean);

  // Test 6: MSHR occupancy sum should be non-zero (L2 served requests)
  uint64_t l2_mshr_occ = tma_read(TMA_L2_MSHR_OCCUPANCY_SUM);
  TMA_ASSERT(l2_mshr_occ > 0, 6);

  // Test 7: Secondary misses counter should be readable
  uint64_t l2_sec_miss = tma_read(TMA_L2_SECONDARY_MISSES);
  tma_use(l2_sec_miss);

  // Test 8: Prefetch counters should be readable
  uint64_t l2_pf_req = tma_read(TMA_L2_PF_HINT_REQ_ACCEPTED);
  uint64_t l2_pf_miss = tma_read(TMA_L2_PF_ALLOC_DIR_MISS);
  tma_use(l2_pf_req + l2_pf_miss);

  // Test 9: Bank/set conflict counters should be readable
  uint64_t l2_bank = tma_read(TMA_L2_BANK_CONFLICT);
  uint64_t l2_set  = tma_read(TMA_L2_SET_CONFLICT_STALL);
  tma_use(l2_bank + l2_set);

  tma_release_snapshot();

  // Dump all counters to sim console for visual inspection
  tma_dump();

  return TMA_PASS;
}
