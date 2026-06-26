/*
 * tma_inject.c — Linux ctor/dtor that snapshots TMA counters on entry and
 * dumps them via BoomPerfCounterDevice's synthesized printf on exit.
 *
 * Linked into a workload binary (SPEC's EXTRA_LIBS).
 *
 * The dtor write to TMA_CTL_DUMP fires the SynthesizePrintf block in
 * generators/boom/.../BoomPerfCounterDevice.scala, which prints all 110
 * counters as `<name> = <value>` lines to the simulator console.
 *
 * Requires running as root for /dev/mem (the firemarshal br-base rootfs
 * runs everything as root). On non-TMA configs the open or mmap will fail
 * and the dtor silently no-ops, preserving graceful degrade.
 *
 * Priority 101 — first user priority above libc-reserved 0..100.
 */

#include <stddef.h>
#include <stdint.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/mman.h>
#include <unistd.h>

#include "tma_counters.h"

#define TMA_INJECT_MMIO_SIZE 0x1000UL

static volatile uint8_t *_tma_base;
static int _tma_fd = -1;

__attribute__((constructor(101)))
static void _tma_ctor(void)
{
    _tma_fd = open("/dev/mem", O_RDWR | O_SYNC);
    if (_tma_fd < 0) {
        perror("tma_inject: open /dev/mem");
        return;
    }

    void *p = mmap(NULL, TMA_INJECT_MMIO_SIZE, PROT_READ | PROT_WRITE,
                   MAP_SHARED, _tma_fd, TMA_MMIO_BASE);
    if (p == MAP_FAILED) {
        perror("tma_inject: mmap TMA_MMIO_BASE");
        close(_tma_fd);
        _tma_fd = -1;
        return;
    }
    _tma_base = (volatile uint8_t *)p;
    fprintf(stderr, "tma_inject: ctor OK, base=%p\n", _tma_base);

    __atomic_store_n((volatile uint64_t *)(_tma_base + TMA_CTL_OFFSET),
                     TMA_CTL_SNAPSHOT, __ATOMIC_SEQ_CST);
}

__attribute__((destructor(101)))
static void _tma_dtor(void)
{
    if (!_tma_base) {
        fprintf(stderr, "tma_inject: dtor no-op, MMIO not mapped\n");
        return;
    }

    // Snapshot2 
    __atomic_store_n((volatile uint64_t *)(_tma_base + TMA_CTL_TWO_OFFSET),
                     TMA_CTL_SNAPSHOT, __ATOMIC_SEQ_CST);

    // Select Snapshot for reading
    __atomic_store_n((volatile uint64_t *)(_tma_base + TMA_READ_SELECT_OFFSET),
                     READ_SNAPSHOT, __ATOMIC_SEQ_CST);

    // Print CSV header
    printf("===== TMA PERFORMANCE COUNTERS =====\n");
    printf("counter,value\n");
    for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
        uint64_t val = *(volatile uint64_t *)(_tma_base + 0x008 + i * 0x008);
        printf("%s,%lu\n", tma_counter_names[i], val);
    }
    printf("====================================\n");

    // Print CSV header and select snapshot 2 for reading
    __atomic_store_n((volatile uint64_t *)(_tma_base + TMA_READ_SELECT_OFFSET),
                     READ_SNAPSHOT2, __ATOMIC_SEQ_CST);
    printf("===== TMA PERFORMANCE COUNTERS =====\n");
    printf("counter,value\n");
    for (int i = 0; i < TMA_NUM_COUNTERS; i++) {
        uint64_t val = *(volatile uint64_t *)(_tma_base + 0x008 + i * 0x008);
        printf("%s,%lu\n", tma_counter_names[i], val);
    }
    printf("====================================\n");


    fflush(stdout);

    __atomic_store_n((volatile uint64_t *)(_tma_base + TMA_CTL_OFFSET),
                     TMA_CTL_RELEASE, __ATOMIC_SEQ_CST);

    munmap((void *)_tma_base, TMA_INJECT_MMIO_SIZE);
    close(_tma_fd);
    _tma_base = NULL;
    _tma_fd = -1;
}
