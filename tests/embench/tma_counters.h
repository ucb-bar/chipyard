#ifndef TMA_COUNTERS_H
#define TMA_COUNTERS_H

#include <stdint.h>

// MMIO base address for the BoomPerfCounterDevice (tile 0)
#define TMA_MMIO_BASE 0x10030000UL

// Control register offsets
#define TMA_CTL_OFFSET      0x000
#define TMA_CTL_SNAPSHOT    0x1   // Write bit 0 to snapshot all counters
#define TMA_CTL_RELEASE     0x2   // Write bit 1 to release snapshot
#define TMA_CTL_DUMP        0x4   // Write bit 2 to dump counters to sim console

// Counter offsets (each 8 bytes)
#define TMA_CYCLES               0x008
#define TMA_INSTRET              0x010
#define TMA_RETIRING             0x018
#define TMA_BAD_SPECULATION      0x020
#define TMA_FRONTEND_BOUND       0x028
#define TMA_BACKEND_BOUND        0x030
#define TMA_FETCH_LATENCY        0x038
#define TMA_FETCH_BANDWIDTH      0x040
#define TMA_BRANCH_MISPREDICT    0x048
#define TMA_MACHINE_CLEARS       0x050
#define TMA_MEMORY_BOUND         0x058
#define TMA_CORE_BOUND           0x060
#define TMA_RETIRED_LOADS        0x068
#define TMA_RETIRED_STORES       0x070
#define TMA_RETIRED_BRANCHES     0x078
#define TMA_RETIRED_JALS         0x080
#define TMA_RETIRED_JALRS        0x088
#define TMA_RETIRED_FP           0x090
#define TMA_RETIRED_AMO          0x098
#define TMA_RETIRED_SYSTEM       0x0A0
#define TMA_ROB_FULL             0x0A8
#define TMA_LDQ_FULL             0x0B0
#define TMA_STQ_FULL             0x0B8
#define TMA_INT_IQ_FULL          0x0C0
#define TMA_MEM_IQ_FULL          0x0C8
#define TMA_BRANCH_MASK_FULL     0x0D0
#define TMA_RENAME_STALL         0x0D8
#define TMA_FLUSH_CYCLES         0x0E0
#define TMA_ROLLBACK_CYCLES      0x0E8
#define TMA_ICACHE_MISS          0x0F0
#define TMA_DCACHE_MISS          0x0F8
#define TMA_DCACHE_RELEASE       0x100
#define TMA_ITLB_MISS            0x108
#define TMA_DTLB_MISS            0x110
#define TMA_L2TLB_MISS           0x118
#define TMA_BR_MISPREDICT        0x120
#define TMA_BR_RESOLVE           0x128
#define TMA_JALR_MISPREDICT      0x130
#define TMA_BR_MISPRED_BPD       0x138
#define TMA_BR_MISPRED_BTB       0x140

// --- L3 TMA counters (100-108): Intel-inspired BOOM-native observability ---
#define TMA_L1D_MISS_PENDING               0x328
#define TMA_DIVIDER_ACTIVE                 0x330
#define TMA_NO_ISSUE                       0x338
#define TMA_ISSUED_C1                      0x340
#define TMA_ISSUED_C2                      0x348
#define TMA_ISSUED_C3                      0x350
#define TMA_ICACHE_STALL                   0x358
#define TMA_ITLB_STALL                     0x360
#define TMA_BRANCH_MISPREDICT_RECOVERY     0x368

// --- L2 extra counter (109): appended to avoid shifting existing indices ---
#define TMA_L2_DEMAND_MISS_PENDING         0x370

// Read a 64-bit counter from the MMIO device
static inline uint64_t tma_read(uint64_t offset) {
  return *(volatile uint64_t *)(TMA_MMIO_BASE + offset);
}

// Write to the control register
static inline void tma_write_ctl(uint64_t value) {
  *(volatile uint64_t *)(TMA_MMIO_BASE + TMA_CTL_OFFSET) = value;
}

// Snapshot all counters atomically
static inline void tma_snapshot(void) {
  tma_write_ctl(TMA_CTL_SNAPSHOT);
}

// Release snapshot (return to live counter values)
static inline void tma_release_snapshot(void) {
  tma_write_ctl(TMA_CTL_RELEASE);
}

// Dump all counters to simulation console (Chisel printf)
static inline void tma_dump(void) {
  tma_write_ctl(TMA_CTL_DUMP);
}

// Counter name table for the original core counters (0-39).
// This table is NOT used by embench benchmarks — boardsupport.c uses tma_dump()
// (hardware MMIO dump of all counters) rather than tma_dump_all() (software iteration).
// For the full 109-counter name table, see tests/tmatests/tma_counters.h.
static const char *tma_counter_names[] = {
  "cycles", "instret",
  "tma_retiring", "tma_bad_speculation", "tma_frontend_bound", "tma_backend_bound",
  "tma_fetch_latency", "tma_fetch_bandwidth",
  "tma_branch_mispredict", "tma_machine_clears",
  "tma_memory_bound", "tma_core_bound",
  "retired_loads", "retired_stores", "retired_branches", "retired_jals",
  "retired_jalrs", "retired_fp", "retired_amo", "retired_system",
  "rob_full_cycles", "ldq_full_cycles", "stq_full_cycles",
  "int_iq_full_cycles", "mem_iq_full_cycles",
  "branch_mask_full_cycles", "rename_stall_cycles",
  "flush_cycles", "rollback_cycles",
  "icache_miss", "dcache_miss", "dcache_release",
  "itlb_miss", "dtlb_miss", "l2tlb_miss",
  "br_mispredict", "br_resolve", "jalr_mispredict",
  "br_mispredict_bpd", "br_mispredict_btb"
};

// Number of counters covered by the name table above (original core counters only).
// The hardware device has 110 counters total; use tma_dump() for a complete hardware dump.
#define TMA_NUM_COUNTERS 40

// Print the first TMA_NUM_COUNTERS counters by name (software iteration).
// Note: embench uses tma_dump() (hardware MMIO dump) instead of this function.
static inline void tma_dump_all(void) {
  tma_snapshot();
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    uint64_t val = tma_read(0x008 + i * 0x008);
    printf("%s: %lu\n", tma_counter_names[i], val);
  }
  tma_release_snapshot();
}

#endif // TMA_COUNTERS_H
