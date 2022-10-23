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
  int r = 0;
  printf("attempting rerocc_acquire\n");

  // Assign trackers 0,1,2,3 to any of the available rerocc accelerators (0,1,2,3)
  // mask is 0xf (0b1111), since we don't care which tracker gets which accelerator
  // (accelerators 0,1,2,3 are identical)
  for (int i = 0; i < 4; i++) {
    while (!rerocc_acquire(i, 0xf)) {}
  }

  // For each tracker, assign opcode 1 to it, then perform the operation
  // The tracker will automatically forward instructions from the assigned opcode
  // the the accelerator allocated to that tracker
  for (int i = 0; i < 4; i++) {
    rerocc_assign(0x1, i);
    r += accum_test();
  }

  // Release all the trackers
  for (int i = 0; i < 4; i++) {
    rerocc_release(i);
  }

  printf("r = %d\n", r);

}


