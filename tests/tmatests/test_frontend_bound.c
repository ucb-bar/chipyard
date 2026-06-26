// Frontend Bound Test — verifies icache pressure causes frontend_bound slots
#include <stdint.h>
#include "tma_counters.h"
#include "tma_test_utils.h"

__attribute__((noinline)) int far_func_a(int x) {
  asm volatile(".rept 128\nnop\n.endr\n");
  return x + 1;
}

__attribute__((noinline)) int far_func_b(int x) {
  asm volatile(".rept 128\nnop\n.endr\n");
  return x + 2;
}

__attribute__((noinline)) int far_func_c(int x) {
  asm volatile(".rept 128\nnop\n.endr\n");
  return x + 3;
}

__attribute__((noinline)) int far_func_d(int x) {
  asm volatile(".rept 128\nnop\n.endr\n");
  return x + 4;
}

int main(void) {
  volatile int sum = 0;
  for (int i = 0; i < 100; i++) sum += i;

  tma_snapshot();
  uint64_t fe_before      = tma_read(TMA_FRONTEND_BOUND);
  uint64_t ic_before      = tma_read(TMA_ICACHE_MISS);
  uint64_t fetch_lat_before = tma_read(TMA_FETCH_LATENCY);
  uint64_t fetch_bw_before  = tma_read(TMA_FETCH_BANDWIDTH);
  tma_release_snapshot();

  for (int i = 0; i < 20; i++) {
    sum = far_func_a(sum);
    sum = far_func_b(sum);
    sum = far_func_c(sum);
    sum = far_func_d(sum);
  }
  tma_use(sum);

  tma_snapshot();
  uint64_t fe_after      = tma_read(TMA_FRONTEND_BOUND);
  uint64_t ic_after      = tma_read(TMA_ICACHE_MISS);
  uint64_t fetch_lat_after = tma_read(TMA_FETCH_LATENCY);
  uint64_t fetch_bw_after  = tma_read(TMA_FETCH_BANDWIDTH);
  tma_release_snapshot();

  uint64_t fe_delta      = fe_after - fe_before;
  uint64_t fetch_lat_delta = fetch_lat_after - fetch_lat_before;
  uint64_t fetch_bw_delta  = fetch_bw_after - fetch_bw_before;

  // Frontend bound should increase with icache pressure
  TMA_ASSERT(fe_delta > 0, 1);
  TMA_ASSERT(ic_after - ic_before > 0, 2);

  // L2 invariant on deltas: fetch_lat + fetch_bw = frontend_bound
  TMA_ASSERT(tma_absdiff(fetch_lat_delta + fetch_bw_delta, fe_delta) <= TMA_TOLERANCE, 3);

  // With large functions causing icache misses, fetch_latency should dominate
  TMA_ASSERT(fetch_lat_delta > 0, 4);

  int rc = check_tma_invariants();
  if (rc) return rc;

  return TMA_PASS;
}
