#ifndef REROCC_H
#define REROCC_H

#include <stdint.h>
#include "rocc.h"

#define REROCC_ACQUIRE (0)
#define REROCC_RELEASE (1)

// Attempts to assign local opcode given by opcode to one of the accelerators in the OH mask
// If no accelerators are available, return 0, else return the oh vector of the assigned accelerator
// Only opcodes 0x1, 0x2, 0x3 are possible
inline uint64_t rerocc_acquire(uint8_t opcode, uint64_t mask) {
  uint64_t op1 = opcode;
  uint64_t op2 = mask;
  uint64_t r;
  ROCC_INSTRUCTION_DSS(0, r, op1, op2, REROCC_ACQUIRE);
  return r;
}

// Releases the accelerator currently allocated to opcode
inline void rerocc_release(uint8_t opcode) {
  uint64_t op1 = opcode;
  ROCC_INSTRUCTION_S(0, op1, REROCC_RELEASE);
}

#endif

