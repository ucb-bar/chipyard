#include "mt-matmul.h"

void matmul_naive(unsigned int coreid, unsigned int ncores, const size_t lda,  const data_t* A, const data_t* B, data_t* C)
{
    size_t i, j, k;

    for (i = 0; i < lda; i++) {
        for (j = coreid; j < lda; j += ncores) {
            data_t sum = 0;
            for (k = 0; k < lda; k++) {
                sum += A[j*lda + k] * B[k*lda + i];
            }
            C[i + j*lda] = sum;
        }
    }
}
