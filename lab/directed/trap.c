#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

void _exit(int);

void handle_trap(uintptr_t epc, uintptr_t cause, uintptr_t tval, uintptr_t regs[32])
{
    printf("TRAP: mepc=0x%08" PRIxPTR " mcause=0x%" PRIxPTR " mtval=0x%08" PRIxPTR "\n", epc, cause, tval);
    _exit(255);
}
