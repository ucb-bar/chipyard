#include "rocc.h"

static inline void accum_write(int idx, unsigned long data)
{
	ROCC_INSTRUCTION_SS(0, data, idx, 0);
}

static inline unsigned long accum_read(int idx)
{
	unsigned long value;
	ROCC_INSTRUCTION_DSS(0, value, 0, idx, 1);
	return value;
}

static inline void accum_load(int idx, void *ptr)
{
	asm volatile ("fence");
	ROCC_INSTRUCTION_SS(0, (uintptr_t) ptr, idx, 2);
}

static inline void accum_add(int idx, unsigned long addend)
{
	ROCC_INSTRUCTION_SS(0, addend, idx, 3);
}

unsigned long data = 0x3421L;

int main(void)
{
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
