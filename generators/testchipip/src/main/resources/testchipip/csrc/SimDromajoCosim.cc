#include <vpi_user.h>
#include <svdpi.h>

#include <stdio.h>
#include <unistd.h>

#include "dromajo_wrapper.h"

// gotten from chipyard elaboration (originally <long_name>.dromajo_params.h)
#include "dromajo_params.h"

#define MAX_ARGS 24

dromajo_t *dromajo = 0;

extern "C" int dromajo_init(
    const char* bootrom_file,
    const char* dtb_file,
    const char* binary_file)
{
    // setup arguments
    char local_argc = 18;
    char* local_argv[MAX_ARGS] = {
        "./dromajo",
        "--compact_bootrom",
        "--custom_extension",
        "--clear_ids",
        "--reset_vector",
        DROMAJO_RESET_VECTOR,
        "--bootrom",
        (char*)bootrom_file,
        "--mmio_range",
        DROMAJO_MMIO_START ":" DROMAJO_MMIO_END,
        "--plic",
        DROMAJO_PLIC_BASE ":" DROMAJO_PLIC_SIZE,
        "--clint",
        DROMAJO_CLINT_BASE ":" DROMAJO_CLINT_SIZE,
        "--memory_size",
        DROMAJO_MEM_SIZE,
        "--save",
        "dromajo_snap"
    };

    if (strlen(dtb_file) != 0) {
        local_argv[local_argc] = "--dtb";
        local_argv[local_argc+1] = (char*)dtb_file;
        local_argc += 2;
    }

    local_argv[local_argc] = (char*)binary_file;
    local_argc += 1;

    if (MAX_ARGS < local_argc) {
        printf("[DRJ_ERR] Too many arguments\n");
        exit(1);
    }

    printf("[DRJ_INFO] Dromajo command: ");
    for (char i = 0; i < local_argc; ++i)
        printf("%s ", local_argv[i]);
    printf("\n");

    dromajo = new dromajo_t(local_argc, local_argv);
    if (!(dromajo->valid_state())) {
        printf("[DRJ_ERR] Failed Dromajo initialization\n");
        return 1;
    }

    return 0;
}

extern "C" int dromajo_step(
    int      hartid,
    long long dut_pc,
    int dut_insn,
    long long dut_wdata,
    long long mstatus,
    bool     check)
{
    return dromajo->step(hartid, dut_pc, dut_insn, dut_wdata, mstatus, check);
}

extern "C" void dromajo_raise_trap(
    int     hartid,
    long long cause)
{
    dromajo->raise_trap(hartid, cause);
}
