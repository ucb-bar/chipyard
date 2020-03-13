#include <stddef.h>

static inline void leak(size_t index, int shift)
{
    register unsigned long a0 asm ("a0") = index;
    register unsigned long a1 asm ("a1") = shift;
    register unsigned long a7 asm ("a7") = 101;

    __asm__ __volatile__ ("ecall"
        : "+r" (+a0)
        : "r" (a1), "r" (a7)
        : "memory");
}

static inline unsigned long get_cycles(void)
{
    unsigned long cycles;
    __asm__ __volatile__ ("rdcycle %0" : "=r" (cycles));
    return cycles;
}

int main(void)
{
    return 0;
}
