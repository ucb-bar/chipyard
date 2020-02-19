#ifndef _RISCV_HPM_H
#define _RISCV_HPM_H

#include <stdint.h>
#include <inttypes.h>
#include <stdio.h>

#ifdef __riscv

#define csr_read(reg) ({ \
    unsigned long __tmp; \
    __asm__ __volatile__ ("csrr %0, " #reg : "=r"(__tmp)); \
    __tmp; })

#define csr_write(reg, val) ({ \
    __asm__ __volatile__ ("csrw " #reg ", %0" :: "rK"(val)); })


#define HPM_NCOUNTERS           16

#define HPM_EVENTSET_BITS       8
#define HPM_EVENTSET_MASK       ((1U << HPM_EVENTSET_BITS) - 1)
#define HPM_EVENT(event, set)   ((1U << ((event) + HPM_EVENTSET_BITS)) | ((set) & HPM_EVENTSET_MASK))

struct hpm {
    uint64_t data[HPM_NCOUNTERS];
};

static inline void hpm_read(struct hpm *hpm)
{
    hpm->data[0] = csr_read(cycle);
    hpm->data[1] = csr_read(instret);
    hpm->data[2] = csr_read(hpmcounter3);
    hpm->data[3] = csr_read(hpmcounter4);
    hpm->data[4] = csr_read(hpmcounter5);
    hpm->data[5] = csr_read(hpmcounter6);
    hpm->data[6] = csr_read(hpmcounter16);
    hpm->data[7] = csr_read(hpmcounter7);
    hpm->data[8] = csr_read(hpmcounter8);
    hpm->data[9] = csr_read(hpmcounter9);
    hpm->data[10] = csr_read(hpmcounter10);
    hpm->data[11] = csr_read(hpmcounter11);
    hpm->data[12] = csr_read(hpmcounter12);
    hpm->data[13] = csr_read(hpmcounter13);
    hpm->data[14] = csr_read(hpmcounter14);
    hpm->data[15] = csr_read(hpmcounter15);
}

static struct hpm hpm_data0;

/* Initialize Rocket hardware performance monitor */
static inline void hpm_init(void)
{
    csr_write(mhpmevent3,  HPM_EVENT(1, 0)); // loads
    csr_write(mhpmevent4,  HPM_EVENT(2, 0)); // stores

    csr_write(mhpmevent5,  HPM_EVENT(0, 2)); // I$ miss
    csr_write(mhpmevent6,  HPM_EVENT(1, 2)); // D$ regular miss
    csr_write(mhpmevent16, HPM_EVENT(6, 2)); // D$ regular miss
    csr_write(mhpmevent7,  HPM_EVENT(2, 2)); // D$ release
    csr_write(mhpmevent8,  HPM_EVENT(3, 2)); // ITLB miss
    csr_write(mhpmevent9,  HPM_EVENT(4, 2)); // DTLB miss
    csr_write(mhpmevent10, HPM_EVENT(5, 2)); // L2 TLB miss

    csr_write(mhpmevent11, HPM_EVENT(6, 0)); // branches
    csr_write(mhpmevent12, HPM_EVENT(5, 1)); // branch misprediction

    csr_write(mhpmevent13, HPM_EVENT(0, 1)); // load-use interlock
    csr_write(mhpmevent14, HPM_EVENT(3, 1)); // I$ blocked
    csr_write(mhpmevent15, HPM_EVENT(4, 1)); // D$ blocked

    hpm_read(&hpm_data0);
}

/* Dump performance counter data */
static inline void hpm_print(void)
{
    static const char *label[] = {
        "cycles",
        "instret",
        "loads",
        "stores",
        "I$ miss",
        "D$ regular miss",
        "D$ prefetch miss",
        "D$ release",
        "ITLB miss",
        "DTLB miss",
        "L2 TLB miss",
        "branches",
        "mispredicts",
        "load-use interlock",
        "I$ blocked",
        "D$ blocked",
    };
    _Static_assert((sizeof(label) / sizeof(char *)) == HPM_NCOUNTERS);
    struct hpm hpm_data1;
    char buf[HPM_NCOUNTERS*32];
    char *bufp;
    int i;

    hpm_read(&hpm_data1);
    for (i = 0, bufp = buf; i < HPM_NCOUNTERS; i++) {
        int n;
        n = sprintf(bufp, "%18s : %" PRIu64 "\n", label[i], hpm_data1.data[i] - hpm_data0.data[i]);
        if (n < 0)
            break;
        bufp += n;
    }
    fputs(buf, stdout);
}

#else /* !__riscv */

static inline void hpm_init()
{
}

static inline void hpm_print()
{
}

#endif /* __riscv */

#endif /* _RISCV_HPM_H */
