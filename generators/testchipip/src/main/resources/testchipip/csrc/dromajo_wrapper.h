#ifndef __DROMAJO_H
#define __DROMAJO_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "dromajo_cosim.h"

class dromajo_t
{
    public:
        dromajo_t(
            int argc,
            char *argv[]
        );

        ~dromajo_t();

        int step(
            int      hartid,
            uint64_t dut_pc,
            uint32_t dut_insn,
            uint64_t dut_wdata,
            uint64_t mstatus,
            bool     check
        );

        void raise_trap(
            int     hartid,
            int64_t cause
        );

        int override_mem(
            int      hartid,
            uint32_t dut_addr,
            uint8_t  dut_size,
            uint64_t dut_wdata
        );

        int valid_state();

    private:
        dromajo_cosim_state_t *state;
};

#endif // __DROMAJO_H
