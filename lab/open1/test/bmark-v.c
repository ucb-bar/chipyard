/*
 * This microbenchmark is compiled to execute in a virtual memory environment
 */
#include <stdio.h>

static inline unsigned long get_cycles(void)
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
