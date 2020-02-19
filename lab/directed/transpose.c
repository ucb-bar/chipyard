#include "dataset.h"
#include "hpm.h"

#define VERIFY

void transpose(mat_nxm_t dst, const mat_mxn_t src)
{
    size_t i, j;
    for (i = 0; i < MAT_M; i++) {
        for (j = 0; j < MAT_N; j++) {
            dst[j][i] = src[i][j];
        }
    }
}

int main(void)
{
    size_t i, j;

    /* Enable performance counters */
    hpm_init();

    transpose(test_dst, test_src);

    /* Print performance counter data */
    hpm_print();

#ifdef VERIFY
    /* Verify result */
    for (i = 0; i < MAT_M; i++) {
        for  (j = 0; j < MAT_N; j++) {
            if (test_dst[j][i] != test_src[i][j]) {
                return 1;
            }
        }
    }
#endif

    return 0;
}
