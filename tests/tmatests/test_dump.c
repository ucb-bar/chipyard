// Dump all TMA counters to simulation console
#include <stdint.h>
#include "tma_counters.h"

int main(void) {
  // Run a small workload
  volatile int sum = 0;
  for (int i = 0; i < 1000; i++) sum += i;

  // Dump counters to verilator stdout
  tma_dump();

  return 0;
}
