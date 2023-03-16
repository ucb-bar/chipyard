#ifndef REROCC_H
#define REROCC_H

#include <stdint.h>
#include <stdbool.h>
#include "rocc.h"

#define REROCC_ACQUIRE (0)
#define REROCC_RELEASE (1)
#define REROCC_ASSIGN (2)
#define REROCC_INFO (3)
#define REROCC_FENCE (4)
#define REROCC_CFLUSH (5)
#define REROCC_CFG_READ_TRACKER (6)
#define REROCC_CFG_READ_ID (7)
#define REROCC_CFG_WRITE_TRACKER (8)
#define REROCC_CFG_WRITE_ID (8)

#define REROCC_CFG_EPOCH (0)
#define REROCC_CFG_RATE (1)
#define REROCC_CFG_LAST_REQS (2)
#define REROCC_CFG_EPOCHRATE (3)



// Attemps to assign a local tracker to one of the accelerators in the OH mask
// If no accelerators are available, return 0, else return the oh vector
// of the assigned accelerator
inline uint64_t rerocc_acquire(uint64_t tracker, uint64_t mask) {
  uint64_t op1 = tracker;
  uint64_t op2 = mask;
  uint64_t r;
  ROCC_INSTRUCTION_DSS(0, r, op1, op2, REROCC_ACQUIRE);
  return r;
}

// Releases the accelerator currently allocated to tracker
inline void rerocc_release(uint64_t tracker) {
  uint64_t op1 = tracker;
  ROCC_INSTRUCTION_S(0, op1, REROCC_RELEASE);
}

// Assigns local opcode given by opcode to one of the trackers
inline void rerocc_assign(uint8_t opcode, uint64_t tracker) {
  uint64_t op1 = tracker;
  uint64_t op2 = opcode;
  ROCC_INSTRUCTION_SS(0, op1, op2, REROCC_ASSIGN);
}

// Gets the number of trackers on this hart
inline uint64_t rerocc_ntrackers() {
  uint64_t r;
  ROCC_INSTRUCTION_D(0, r, REROCC_INFO);
  return r;
}

// Fences a specific single tracker on this hart
inline void rerocc_fence(uint64_t tracker) {
  uint64_t op1 = tracker;
  ROCC_INSTRUCTION_S(0, op1, REROCC_FENCE);
  asm volatile("fence");
}

inline void rerocc_cflush(void* addr) {
  uint64_t op1 = (uint64_t)addr;
  ROCC_INSTRUCTION_S(0, op1, REROCC_CFLUSH);
}

inline uint64_t rerocc_write_cfg_tracker(uint64_t tracker, uint64_t wdata, uint32_t cfg_id, bool read) {
  uint64_t op1 = ((uint64_t) cfg_id << 32) | (tracker & 0xffffffff);
  uint64_t op2 = wdata;
  uint64_t r = 0;
  if (read) {
    ROCC_INSTRUCTION_DSS(0, r, op1, op2, REROCC_CFG_WRITE_TRACKER);
  } else {
    ROCC_INSTRUCTION_SS(0, op1, op2, REROCC_CFG_WRITE_TRACKER);
  }
  return r;
}

inline uint64_t rerocc_write_cfg_mgr_id(uint64_t id, uint64_t wdata, uint32_t cfg_id, bool read) {
  uint64_t op1 = ((uint64_t) cfg_id << 32) | (id & 0xffffffff);
  uint64_t op2 = wdata;
  uint64_t r = 0;
  if (read) {
    ROCC_INSTRUCTION_DSS(0, r, op1, op2, REROCC_CFG_WRITE_ID);
  } else {
    ROCC_INSTRUCTION_SS(0, op1, op2, REROCC_CFG_WRITE_ID);
  }
  return r;
}

inline uint64_t rerocc_read_cfg_tracker(uint64_t tracker, uint32_t cfg_id) {
  uint64_t op1 = ((uint64_t) cfg_id << 32) | (tracker & 0xffffffff);
  uint64_t r;
  ROCC_INSTRUCTION_DS(0, r, op1, REROCC_CFG_READ_TRACKER);
  return r;
}

inline uint64_t rerocc_read_cfg_mgr_id(uint64_t id, uint32_t cfg_id) {
  uint64_t op1 = ((uint64_t) cfg_id << 32) | (id & 0xffffffff);
  uint64_t r;
  ROCC_INSTRUCTION_DS(0, r, op1, REROCC_CFG_READ_ID);
  return r;
}

inline uint64_t rerocc_rateset_tracker(uint64_t tracker, uint64_t epoch, uint64_t max_req, bool read) {
  uint64_t wdata = (epoch << 32) | (max_req & 0xffffffff);
  return rerocc_write_cfg_tracker(tracker, wdata, REROCC_CFG_EPOCHRATE, read);
}

/* // address falls into this range redirects to DRAM by bypassing */
/* // can have multiple configured bypass address range at the same time */
/* // initialize configured bypass range upon rerocc_fence */
/* inline void rerocc_bypass(uint64_t tracker, void* addr_start, void* addr_end){                     */
/*   uint64_t op1 = ((uint64_t) addr_end << 16) | tracker; */
/*   uint64_t op2 = (uint64_t) addr_start; */
/*   ROCC_INSTRUCTION_SS(0, op1, op2, REROCC_BYPASS); */
/* }     */

#endif

