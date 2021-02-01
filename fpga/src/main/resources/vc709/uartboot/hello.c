#include <stdint.h>
#include "./include/platform.h"
#include "./include/common.h"

#define DEBUG
#include "kprintf.h"

#define MAX_CORES 8

int main(void)
{
	kputs("this is hello\n");
	return 0;
}
