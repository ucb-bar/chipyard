/*
 * NOTE: This microbenchmark is built to run in a physical environment
 */
#include <stdio.h>

static inline unsigned long rdcycle(void)
{
    unsigned long cycles;
    __asm__ __volatile__ ("rdcycle %0" : "=r" (cycles));
    return cycles;
}

int main(void)
{
    /* TODO: Write your code here */
    return 0;
}
