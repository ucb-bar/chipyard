// Bare-metal mirror of tma_inject's ctor+dtor sequence.
// Skips Linux + mmap; writes/reads MMIO directly. Used to test the
// SynthesizePrintf path on FireSim without paying Linux-boot wallclock.
#include <stdint.h>
#include "tma_counters.h"
#include <stdio.h>

int main(void) {
  // ctor-equivalent: snapshot now
  tma_snapshot();

  // small workload between snapshot and dump
  volatile uint64_t sum = 0;
  for (uint64_t i = 0; i < 100; i++) sum += i;

  tma_snapshot2();

  // dtor-equivalent: dump (no release — match tma_inject's last variant)
  tma_dump();

  tma_print_snapshots_diff();

  // we expect the dump and print results to be about the same

  tma_release_snapshot();
  tma_release_snapshot2();

  // try again, should see diff values if snapshot release correct

  // ctor-equivalent: snapshot now
  tma_snapshot();

  // small workload between snapshot and dump
  for (uint64_t i = 0; i < 10; i++) sum += i;

  tma_snapshot2();

  // dtor-equivalent: dump (no release — match tma_inject's last variant)
  tma_dump();

  tma_print_snapshots_diff();

}
