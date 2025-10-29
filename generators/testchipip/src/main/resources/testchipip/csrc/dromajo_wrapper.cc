#include "dromajo_wrapper.h"

dromajo_t::dromajo_t(int argc, char *argv[])
{
    // call dromajo's init function
    this->state = dromajo_cosim_init(argc, argv);
}

dromajo_t::~dromajo_t()
{
    // call dromajo's finish function
    dromajo_cosim_fini(this->state);
}

int dromajo_t::step(
    int      hartid,
    uint64_t dut_pc,
    uint32_t dut_insn,
    uint64_t dut_wdata,
    uint64_t mstatus,
    bool     check)
{
    // call dromajo's step function
    return dromajo_cosim_step(this->state, hartid, dut_pc, dut_insn, dut_wdata, mstatus, check);
}

void dromajo_t::raise_trap(
    int     hartid,
    int64_t cause)
{
    // call dromajo's raise trap function
    dromajo_cosim_raise_trap(this->state, hartid, cause);
}

int dromajo_t::override_mem(
    int      hartid,
    uint32_t dut_addr,
    uint8_t  dut_size,
    uint64_t dut_wdata)
{
    // call dromajo's override mem function
    return dromajo_cosim_override_mem(this->state, hartid, dut_addr, dut_wdata, dut_size);
}

int dromajo_t::valid_state()
{
    return (this->state != 0);
}
