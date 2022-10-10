#include "rerocc.h"
#include <stdio.h>


static inline void accum_write(int idx, unsigned long data)
{
	ROCC_INSTRUCTION_SS(1, data, idx, 0);
}

static inline unsigned long accum_read(int idx)
{
	unsigned long value;
	ROCC_INSTRUCTION_DSS(1, value, 0, idx, 1);
	return value;
}

static inline void accum_load(int idx, void *ptr)
{
	asm volatile ("fence");
	ROCC_INSTRUCTION_SS(1, (uintptr_t) ptr, idx, 2);
}

static inline void accum_add(int idx, unsigned long addend)
{
	ROCC_INSTRUCTION_SS(1, addend, idx, 3);
}


int accum_test() {
  unsigned long data = 0x3421;
  unsigned long result;

  accum_load(0, &data);
  accum_add(0, 2);
  result = accum_read(0);

  if (result != data + 2)
    return 1;

  accum_write(0, 3);
  accum_add(0, 1);
  result = accum_read(0);

  if (result != 4)
    return 2;

  return 0;
}

int main(void) {
  int r;
  printf("attempting rerocc_acquire\n");

  // First argument is opcode, second argument is accelerator mask
  // This function attempts to assign one of the accelerators in the mask
  // to opcode 0x1. 0x1, 0x2, 0x3 are possible opcodes to assign
  // rerocc_acquire will return 0 until it acquires an accelerator, at which point it returns 1
  while (!rerocc_acquire(1, 0x1)) {}
  // Do accelerator stuff
  r += accum_test();
  // Release the accelerator currently allocated to opcode 1
  rerocc_release(1);

  while (!rerocc_acquire(1, 0x1)) {}
  r += accum_test();
  rerocc_release(1);

  printf("r = %d\n", r);

}


