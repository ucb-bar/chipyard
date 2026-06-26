#ifndef TMA_COUNTERS_H
#define TMA_COUNTERS_H

#include <stdio.h>
#include <stdint.h>

// MMIO base address for the BoomPerfCounterDevice (tile 0)
#define TMA_MMIO_BASE 0x10030000UL

// Control register offsets
#define TMA_CTL_OFFSET      0x000
#define TMA_CTL_SNAPSHOT    0x1   // Write bit 0 to snapshot all counters
#define TMA_CTL_RELEASE     0x2   // Write bit 1 to release snapshot
#define TMA_CTL_DUMP        0x4   // Write bit 2 to dump counters to sim console

#define READ_DEFAULT 0x0
#define READ_SNAPSHOT 0x1
#define READ_SNAPSHOT2 0x2
#define READ_LIVE 0x3

// Counter offsets (each 8 bytes)
// --- Core counters (0-39) ---
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

// --- New core counters (40-59) ---
#define TMA_DISPATCH_SLOTS_VALID   0x148
#define TMA_ISSUED_INT_TOTAL       0x150
#define TMA_ISSUED_MEM_TOTAL       0x158
#define TMA_ISSUED_MUL_TOTAL       0x160
#define TMA_ISSUED_DIV_TOTAL       0x168
#define TMA_FLUSH_XCPT_EVENTS      0x170
#define TMA_FLUSH_ERET_EVENTS      0x178
#define TMA_FLUSH_REFETCH_EVENTS   0x180
#define TMA_FLUSH_NEXT_EVENTS      0x188
#define TMA_DIS_STALL_CYCLES       0x190
#define TMA_BR_COND_MISPREDICT     0x198
#define TMA_BR_INDIRECT_MISPREDICT 0x1A0
#define TMA_BR_RET_MISPREDICT      0x1A8
#define TMA_BR_NO_PREDICTION       0x1B0
#define TMA_FETCH_BUBBLE_RAW       0x1B8
#define TMA_FETCH_SLOTS_DELIVERED  0x1C0
#define TMA_DECODE_BACKEND_STALL   0x1C8
#define TMA_INT_IQ_EMPTY_CYCLES    0x1D0
#define TMA_MEM_IQ_EMPTY_CYCLES    0x1D8
#define TMA_SFB_OPT_EVENTS         0x1E0

// --- Memory Ordering counters (60-67) ---
#define TMA_STLD_FWD_STALL_CYCLES          0x1E8
#define TMA_STLD_FWD_SUCCESS               0x1F0
#define TMA_STLD_FWD_WAKEUP_RETRIES        0x1F8
#define TMA_STLD_FWD_BLOCK_LOAD_WAKEUP    0x200
#define TMA_MEM_ORDER_FAILURES             0x208
#define TMA_LOAD_ORDERING_FAILURES         0x210
#define TMA_LOAD_SPEC_MISPREDICT           0x218
#define TMA_LOAD_NACK_RETRIES              0x220

// --- Data Dependency counters (68-74) ---
#define TMA_DEP_STALL_CYCLES               0x228
#define TMA_OPERAND_WAIT_SLOT_CYCLES       0x230
#define TMA_IQ_DISPATCHED_READY            0x238
#define TMA_IQ_DISPATCHED_NOT_READY        0x240
#define TMA_ISSUED_WITH_POISON             0x248
#define TMA_LDSPEC_SQUASH_GRANTS           0x250
#define TMA_SPEC_LD_WAKEUP_EVENTS          0x258

// --- L2 Cache counters (75-91) ---
#define TMA_L2_PF_HINT_REQ_ACCEPTED        0x260
#define TMA_L2_PF_HINT_REQ_BLOCKED         0x268
#define TMA_L2_PF_ALLOC_DIR_MISS           0x270
#define TMA_L2_PF_ALLOC_DIR_HIT            0x278
#define TMA_L2_DEMAND_ALLOC_DIR_MISS       0x280
#define TMA_L2_DEMAND_HIT_PREFETCHED       0x288
#define TMA_L2_DEMAND_HIT_PF_BROUGHT       0x290
#define TMA_L2_DEMAND_QUEUED_BEHIND_PF     0x298
#define TMA_L2_DEMAND_HIT_REGULAR          0x2A0
#define TMA_L2_SECONDARY_MISSES            0x2A8
#define TMA_L2_EVICTIONS_DIRTY             0x2B0
#define TMA_L2_EVICTIONS_CLEAN             0x2B8
#define TMA_L2_EVICTIONS_PREFETCHED        0x2C0
#define TMA_L2_MSHR_OCCUPANCY_SUM          0x2C8
#define TMA_L2_MSHR_FULL_CYCLES            0x2D0
#define TMA_L2_SET_CONFLICT_STALL          0x2D8
#define TMA_L2_BANK_CONFLICT               0x2E0

// --- OOO engine counters (92-98) ---
#define TMA_INT_PREG_STALL                 0x2E8
#define TMA_FP_PREG_STALL                  0x2F0
#define TMA_RETIRE_WIDTH_0                 0x2F8
#define TMA_RETIRE_WIDTH_1                 0x300
#define TMA_RETIRE_WIDTH_2                 0x308
#define TMA_RETIRE_WIDTH_3                 0x310
#define TMA_RETIRE_WIDTH_4                 0x318

// --- Fetch/decode counters (99) ---
#define TMA_ICACHE_LOOKUPS                 0x320

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

// -- Extra Control -- 
#define TMA_CTL_TWO_OFFSET 0x378
#define TMA_READ_SELECT_OFFSET 0x380

// Read a 64-bit counter from the MMIO device
static inline uint64_t tma_read(uint64_t offset) {
  return *(volatile uint64_t *)(TMA_MMIO_BASE + offset);
}

// Write to the control register
static inline void tma_write_ctl(uint64_t value) {
  *(volatile uint64_t *)(TMA_MMIO_BASE + TMA_CTL_OFFSET) = value;
}

static inline void tma_write_ctl2(uint64_t value) {
  *(volatile uint64_t *)(TMA_MMIO_BASE + TMA_CTL_TWO_OFFSET) = value;
}

static inline void tma_write_read_select(uint64_t value) {
  *(volatile uint64_t *)(TMA_MMIO_BASE + TMA_READ_SELECT_OFFSET) = value;
}

// Snapshot all counters atomically
static inline void tma_snapshot(void) {
  tma_write_ctl(TMA_CTL_SNAPSHOT);
}

static inline void tma_snapshot2(void) {
  tma_write_ctl2(TMA_CTL_SNAPSHOT);
}

// Release snapshot (return to live counter values)
static inline void tma_release_snapshot(void) {
  tma_write_ctl(TMA_CTL_RELEASE);
}

// Release snapshot (return to live counter values)
static inline void tma_release_snapshot2(void) {
  tma_write_ctl2(TMA_CTL_RELEASE);
}


// Dump all counters to simulation console (Chisel printf)
static inline void tma_dump(void) {
  tma_write_ctl(TMA_CTL_DUMP);
}

// Counter name table for printing
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
  "br_mispredict_bpd", "br_mispredict_btb",
  // New core counters
  "dispatch_slots_valid",
  "issued_int_total", "issued_mem_total", "issued_mul_total", "issued_div_total",
  "flush_xcpt", "flush_eret", "flush_refetch", "flush_next",
  "dis_stall",
  "br_cond_mispredict", "br_indirect_mispredict", "br_ret_mispredict", "br_no_prediction",
  "fetch_bubble_raw", "fetch_slots_delivered", "decode_backend_stall",
  "int_iq_empty", "mem_iq_empty", "sfb_opt_events",
  // Memory ordering counters
  "stld_fwd_stall_cycles", "stld_fwd_success", "stld_fwd_wakeup_retries",
  "stld_fwd_block_load_wakeup", "mem_order_failures",
  "load_ordering_failures", "load_spec_mispredict", "load_nack_retries",
  // Data dependency counters
  "dep_stall_cycles", "operand_wait_slot_cycles",
  "iq_dispatched_ready", "iq_dispatched_not_ready",
  "issued_with_poison", "ldspec_squash_grants", "spec_ld_wakeup_events",
  // L2 cache counters
  "l2_pf_hint_req_accepted", "l2_pf_hint_req_blocked",
  "l2_pf_alloc_dir_miss", "l2_pf_alloc_dir_hit",
  "l2_demand_alloc_dir_miss", "l2_demand_hit_prefetched",
  "l2_demand_hit_pf_brought", "l2_demand_queued_behind_pf",
  "l2_demand_hit_regular",
  "l2_secondary_misses", "l2_evict_dirty", "l2_evict_clean",
  "l2_evict_prefetched",
  "l2_mshr_occ_sum", "l2_mshr_full",
  "l2_set_conflict_stall", "l2_bank_conflict",
  // OOO engine counters
  "int_preg_stall_cycles", "fp_preg_stall_cycles",
  "retire_width_0_cycles", "retire_width_1_cycles", "retire_width_2_cycles",
  "retire_width_3_cycles", "retire_width_4_cycles",
  // Fetch/decode counters
  "icache_lookups",
  // L3 TMA counters
  "l1d_miss_pending", "divider_active",
  "no_issue", "issued_c1", "issued_c2", "issued_c3",
  "icache_stall", "itlb_stall", "branch_mispredict_recovery",
  // L2 extra counter
  "l2_demand_miss_pending"
};

#define TMA_NUM_COUNTERS 110

// Print all counters (requires printf support)
static inline void tma_dump_all(void) {
  tma_snapshot();
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    uint64_t val = tma_read(0x008 + i * 0x008);
    printf("%s: %lu\n", tma_counter_names[i], val);
  }
  tma_release_snapshot();
}

// Print diff between snapshots
static inline void tma_print_snapshots_diff(void) {
  uint64_t snapshot_vals[TMA_NUM_COUNTERS];
  uint64_t snapshot2_vals[TMA_NUM_COUNTERS];
  tma_write_read_select(READ_SNAPSHOT);
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    uint64_t val = tma_read(0x008 + i * 0x008);
    snapshot_vals[i] = val;
  }
  tma_write_read_select(READ_SNAPSHOT2);
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    uint64_t val = tma_read(0x008 + i * 0x008);
    snapshot2_vals[i] = val;
  }
  for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
    printf("%s: %lu\n", tma_counter_names[i], snapshot2_vals[i] - snapshot_vals[i]);
  }
}

#endif // TMA_COUNTERS_H
