// See LICENSE for license details.

//**************************************************************************
// Multithreaded matrix multiply benchmark
//--------------------------------------------------------------------------

#include <stdio.h>
#include "mt-matmul.h"

//--------------------------------------------------------------------------
// Basic Utilities and Multithreading Support
#include "util.h"

//--------------------------------------------------------------------------
// Main
//
// All threads start executing main(). Use their "coreid" to
// differentiate between threads (each thread is running on a separate
// core).

int main(void)
{
    static data_t output_data[ARRAY_SIZE];

    const unsigned int coreid = read_csr(mhartid);
    struct stats st;

    barrier();
    stats_init(&st);
    MATMUL_FUNC(coreid, nharts, DIM_SIZE, input1_data, input2_data, output_data);

    if (barrier()) {
        // Last thread collects statistics and checks result
        int rc;
        stats_print(&st, stringify(MATMUL_FUNC), DIM_SIZE * DIM_SIZE * DIM_SIZE);
        rc = verify(ARRAY_SIZE, output_data, verify_data);
        if (rc != 0) {
            puts("\nactual matrix: ");
            print_matrix(output_data, DIM_SIZE, DIM_SIZE);
            puts("\ncorrect matrix: ");
            print_matrix(verify_data, DIM_SIZE, DIM_SIZE);
        }
        return rc;
    } else {
        for (;;);
    }
}
