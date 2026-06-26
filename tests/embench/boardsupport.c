/* Board support for Embench on Chipyard RISC-V bare-metal */

#include "tma_counters.h"

void initialise_board(void)
{
}

void start_trigger(void)
{
    /* Snapshot TMA counters at benchmark start */
    tma_snapshot();
}

void stop_trigger(void)
{
    /* Dump TMA counters to simulation console */
    tma_dump();
    tma_release_snapshot();
}
