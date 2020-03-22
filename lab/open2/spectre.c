#include <stddef.h>
#include <unistd.h>

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

/* Specialized output function to avoid printf() overhead
   Use this to emit results for the autograder, one byte per line */
static inline void printx(unsigned char x)
{
    static const char hex[] = "0123456789abcdef";
    char buf[3];

    buf[0] = hex[(x >> 4) & 0xf];
    buf[1] = hex[x & 0xf];
    buf[2] = '\n';
    write(STDOUT_FILENO, buf, sizeof(buf));
}

int main(void)
{
    return 0;
}
