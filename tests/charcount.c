#include "rocc.h"

char string[64] = "The quick brown fox jumped over the lazy dog";

static inline unsigned long count_chars(char *start, char needle)
{
	unsigned long count;
	asm volatile ("fence");
	ROCC_INSTRUCTION_DSS(2, count, start, needle, 0);
	return count;
}

int main(void)
{
	unsigned long count = count_chars(string + 14, 'o');
	if (count != 3)
		return count + 1;
	return 0;
}
