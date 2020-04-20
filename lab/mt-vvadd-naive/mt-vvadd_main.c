// See LICENSE for license details.

//**************************************************************************
// Multithreaded vector-vector add benchmark
//--------------------------------------------------------------------------

#include <stdio.h>
#include "mt-vvadd.h"

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
    static data_t output_data[DATA_SIZE];

    const unsigned int coreid = read_csr(mhartid);
    struct stats st;

    barrier();
    stats_init(&st);
    VVADD_FUNC(coreid, nharts, DATA_SIZE, input1_data, input2_data, output_data);

    if (barrier()) {
        // Last thread collects statistics and checks result
        int rc;
        stats_print(&st, stringify(VVADD_FUNC), DATA_SIZE);
        rc = verify_double(DATA_SIZE, output_data, verify_data);
        if (rc != 0) {
            puts("\nactual array:");
            print_double_array(output_data, DATA_SIZE);
            puts("\ncorrect array:");
            print_double_array(verify_data, DATA_SIZE);
        }
        return rc;
    } else {
        for (;;);
    }
}
