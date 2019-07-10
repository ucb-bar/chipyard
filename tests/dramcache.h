#ifndef __DRAM_CACHE_H__
#define __DRAM_CACHE_H__

#include "mmio.h"
#include "rocc.h"

#define EXTENT_TABLE_BASE 0x10020000L
#define STREAMBUF_CTRL_BASE 0x1001f000L

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

#endif
