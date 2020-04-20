#include "mt-vvadd.h"

void vvadd_naive(int coreid, int ncores, size_t n, const data_t* x, const data_t* y, data_t* z)
{
    size_t i;
    // Interleave accesses
    for (i = coreid; i < n; i += ncores) {
        z[i] = x[i] + y[i];
    }
}
