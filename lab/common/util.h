#ifndef _UTIL_H
#define _UTIL_H

#include <stdio.h>

extern const unsigned int nharts;

extern int barrier(void);


static inline int verify(size_t n, const volatile int* test, const int* verify)
{
    size_t i;
    // Unrolled for faster verification
    for (i = 0; i < (n/2*2); i += 2) {
        int t0 = test[i], t1 = test[i+1];
        int v0 = verify[i], v1 = verify[i+1];
        if (t0 != v0)
            return i+1;
        if (t1 != v1)
            return i+2;
    }
    if ((n % 2) && test[n-1] != verify[n-1])
        return n;
    return 0;
}

static inline int verify_double(size_t n, const double* test, const double* verify)
{
    size_t i;
    // Unrolled for faster verification
    for (i = 0; i < (n/2*2); i += 2) {
        double t0 = test[i], t1 = test[i+1];
        double v0 = verify[i], v1 = verify[i+1];
        int eq1 = t0 == v0, eq2 = t1 == v1;
        if (!(eq1 & eq2))
            return i+1+eq1;
    }
    if ((n % 2) && (test[n-1] != verify[n-1]))
        return n;
    return 0;
}

static inline void print_array(const int *data, size_t n)
{
    for (; n > 1; n--) {
        printf("%d ", *data++);
    }
    printf("%d\n", *data);
}

static inline void print_matrix(const int *data, size_t m, size_t n)
{
    for (; m > 0; m--, data += n) {
        print_array(data, n);
    }
}

static inline void print_double_array(const double *data, size_t n)
{
    for (; n > 0; n--) {
        double d = *data++;
        printf("%ld.%ld ", (long)d, ((long)(d * 10)) % 10);
    }
    puts("");
}


#define read_csr(reg) __extension__ ({ \
    unsigned long __tmp; \
    __asm__ __volatile__ ("csrr %0, " #reg : "=r"(__tmp)); \
    __tmp; })

struct stats {
    unsigned long cycle;
    unsigned long instret;
};

static inline void stats_init(struct stats *st) {
    st->cycle = read_csr(mcycle);
    st->instret= read_csr(minstret);
}

static inline void stats_print(const struct stats *st, const char *str, size_t iter) {
    unsigned long cycle, instret;
    cycle = read_csr(cycle);
    instret = read_csr(minstret);
    cycle -= st->cycle;
    instret -= st->instret;
    printf("\n%s: %ld cycles, %ld.%ld cycles/iter, %ld.%ld CPI\n",
        str, cycle, cycle/iter, ((10*cycle)/iter) % 10,
        cycle/instret, ((10*cycle)/instret) % 10);

}

#define __stringify(s) #s
#define stringify(s) __stringify(s)

#endif /* _UTIL_H */
