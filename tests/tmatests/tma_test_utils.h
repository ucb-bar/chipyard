#ifndef TMA_TEST_UTILS_H
#define TMA_TEST_UTILS_H

#include <stdint.h>
#include "tma_counters.h"

// Fail codes: return value encodes which assertion failed (1-based)
#define TMA_PASS 0
#define TMA_FAIL(n) (n)

// Simple assertion macro: returns fail code n if condition is false
#define TMA_ASSERT(cond, n) do { \
  if (!(cond)) return TMA_FAIL(n); \
} while(0)

// Volatile sink to prevent optimization
static volatile uint64_t tma_sink;
static inline void tma_use(uint64_t val) { tma_sink = val; }

// Tolerance for exact equality checks
// MegaBoom corewidth * 4
#define TMA_TOLERANCE 64

// Absolute difference helper
static inline uint64_t tma_absdiff(uint64_t a, uint64_t b) {
  return (a > b) ? (a - b) : (b - a);
}

// Check all TMA invariants. Returns 0 on pass, 100+N on invariant N failure.
// Call at the end of every test to validate counter consistency.
static int check_tma_invariants(void) {
  tma_snapshot();

  uint64_t cycles      = tma_read(TMA_CYCLES);
  uint64_t instret     = tma_read(TMA_INSTRET);
  uint64_t retiring    = tma_read(TMA_RETIRING);
  uint64_t bad_spec    = tma_read(TMA_BAD_SPECULATION);
  uint64_t fe_bound    = tma_read(TMA_FRONTEND_BOUND);
  uint64_t be_bound    = tma_read(TMA_BACKEND_BOUND);
  uint64_t fetch_lat   = tma_read(TMA_FETCH_LATENCY);
  uint64_t fetch_bw    = tma_read(TMA_FETCH_BANDWIDTH);
  uint64_t br_misp_l2  = tma_read(TMA_BRANCH_MISPREDICT);
  uint64_t mach_clr    = tma_read(TMA_MACHINE_CLEARS);
  uint64_t mem_bound   = tma_read(TMA_MEMORY_BOUND);
  uint64_t core_bound  = tma_read(TMA_CORE_BOUND);

  uint64_t ret_loads   = tma_read(TMA_RETIRED_LOADS);
  uint64_t ret_stores  = tma_read(TMA_RETIRED_STORES);
  uint64_t ret_br      = tma_read(TMA_RETIRED_BRANCHES);
  uint64_t ret_jals    = tma_read(TMA_RETIRED_JALS);
  uint64_t ret_jalrs   = tma_read(TMA_RETIRED_JALRS);
  uint64_t ret_fp      = tma_read(TMA_RETIRED_FP);
  uint64_t ret_amo     = tma_read(TMA_RETIRED_AMO);
  uint64_t ret_sys     = tma_read(TMA_RETIRED_SYSTEM);

  uint64_t br_misp     = tma_read(TMA_BR_MISPREDICT);
  uint64_t br_resolve  = tma_read(TMA_BR_RESOLVE);
  uint64_t jalr_misp   = tma_read(TMA_JALR_MISPREDICT);
  uint64_t misp_bpd    = tma_read(TMA_BR_MISPRED_BPD);
  uint64_t misp_btb    = tma_read(TMA_BR_MISPRED_BTB);

  tma_release_snapshot();

  uint64_t slot_sum = retiring + bad_spec + fe_bound + be_bound;

  // Invariant 1: slot_sum = cycles * coreWidth
  // We don't know coreWidth at runtime, so check slot_sum is a valid multiple
  if (cycles > 0) {
    uint64_t remainder = slot_sum % cycles;
    uint64_t width = slot_sum / cycles;
    // Allow small tolerance on remainder
    if (remainder > TMA_TOLERANCE && (cycles - remainder) > TMA_TOLERANCE)
      return 101;
    if (width == 0 || width > 8)
      return 101;
  }

  // Invariant 2: fetch_latency + fetch_bandwidth = frontend_bound
  if (tma_absdiff(fetch_lat + fetch_bw, fe_bound) > TMA_TOLERANCE)
    return 102;

  // Invariant 3: branch_mispredict + machine_clears = bad_speculation
  if (tma_absdiff(br_misp_l2 + mach_clr, bad_spec) > TMA_TOLERANCE)
    return 103;

  // Invariant 4: memory_bound + core_bound = backend_bound
  if (tma_absdiff(mem_bound + core_bound, be_bound) > TMA_TOLERANCE)
    return 104;

  // Invariant 5: instret <= retiring
  if (instret > retiring + TMA_TOLERANCE)
    return 105;

  // Invariant 6: jalr_mispredict <= br_mispredict
  if (jalr_misp > br_misp + TMA_TOLERANCE)
    return 106;

  // Invariant 7: br_mispredict <= br_resolve
  if (br_misp > br_resolve + TMA_TOLERANCE)
    return 107;

  // Invariant 8: br_mispred_bpd + br_mispred_btb <= br_mispredict
  if (misp_bpd + misp_btb > br_misp + TMA_TOLERANCE)
    return 108;

  // Invariant 9: sum(retired instruction types) <= instret
  {
    uint64_t type_sum = ret_loads + ret_stores + ret_br + ret_jals +
                        ret_jalrs + ret_fp + ret_amo + ret_sys;
    if (type_sum > instret + TMA_TOLERANCE)
      return 109;
  }

  return 0;
}

#endif
