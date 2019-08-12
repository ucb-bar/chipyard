#ifndef __DRAM_CACHE_H__
#define __DRAM_CACHE_H__

#include "mmio.h"
#include "rocc.h"

#define L2_CACHE_CTL_BASE 0x2010000L
#define L2_CACHE_FLUSH 0x200

#define DRAM_CACHE_CTL_BASE 0x1001f000L
#define DRAM_CACHE_HITS   0x00
#define DRAM_CACHE_MISSES 0x08
#define DRAM_CACHE_READS  0x10
#define DRAM_CACHE_WRITES 0x18
#define DRAM_CACHE_HINTS  0x20
#define DRAM_CACHE_WRITEBACKS 0x28
#define DRAM_CACHE_FLUSH  0x30

#define EXTENT_TABLE_BASE 0x10020000L

static inline void set_extent_mapping(
		long logExtent, long physExtent, long macsuffix) {
	unsigned long addr = EXTENT_TABLE_BASE + logExtent * 8;
	unsigned long entry = (macsuffix << 48) | (1L << 47) | physExtent;

	reg_write64(addr, entry);
}

static inline void prefetch_read(unsigned long addr, unsigned long len)
{
	ROCC_INSTRUCTION_SS(2, addr, len, 0);
}

static inline void prefetch_write(unsigned long addr, unsigned long len)
{
	ROCC_INSTRUCTION_SS(2, addr, len, 1);
}

static inline void enable_auto_prefetch(void)
{
	ROCC_INSTRUCTION_S(2, 1, 65);
}

static inline void disable_auto_prefetch(void)
{
	ROCC_INSTRUCTION_S(2, 0, 65);
}

static inline void flush_line(unsigned long addr)
{
	reg_write64(L2_CACHE_CTL_BASE + L2_CACHE_FLUSH, addr);
	asm volatile ("fence");
	reg_write64(DRAM_CACHE_CTL_BASE + DRAM_CACHE_FLUSH, addr);
	asm volatile ("fence");
}

static inline long cache_counter(int counter)
{
	asm volatile ("fence");
	return reg_read64(DRAM_CACHE_CTL_BASE + counter);
}

#endif
