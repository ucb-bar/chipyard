#ifndef __MMIO_H__
#define __MMIO_H__

static inline void write_reg(unsigned long addr, unsigned int data)
{
	volatile unsigned int *ptr = (volatile unsigned int *) addr;
	*ptr = data;
}

static inline unsigned long read_reg(unsigned long addr)
{
	volatile unsigned int *ptr = (volatile unsigned int *) addr;
	return *ptr;
}

#endif
