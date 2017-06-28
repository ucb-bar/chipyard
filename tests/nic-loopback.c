#include "mmio.h"
#include <stdlib.h>
#include <stdio.h>

#define SIMPLENIC_BASE 0x10016000L
#define SIMPLENIC_SEND_REQ (SIMPLENIC_BASE + 0)
#define SIMPLENIC_RECV_REQ (SIMPLENIC_BASE + 8)
#define SIMPLENIC_SEND_COMP (SIMPLENIC_BASE + 16)
#define SIMPLENIC_RECV_COMP (SIMPLENIC_BASE + 18)
#define SIMPLENIC_COUNTS (SIMPLENIC_BASE + 20)

uint64_t src[200];
uint64_t dst[201];

static inline int nic_send_req_avail(void)
{
	return reg_read16(SIMPLENIC_COUNTS) & 0xf;
}

static inline int nic_recv_req_avail(void)
{
	return (reg_read16(SIMPLENIC_COUNTS) >> 4) & 0xf;
}

static inline int nic_send_comp_avail(void)
{
	return (reg_read16(SIMPLENIC_COUNTS) >> 8) & 0xf;
}

static inline int nic_recv_comp_avail(void)
{
	return (reg_read16(SIMPLENIC_COUNTS) >> 12) & 0xf;
}

static void nic_send(void *data, unsigned long len)
{
	uintptr_t addr = ((uintptr_t) data) & ((1L << 48) - 1);
	unsigned long packet = (len << 48) | addr;

	while (nic_send_req_avail() == 0);
	reg_write64(SIMPLENIC_SEND_REQ, packet);
}

static int nic_recv(void *dest)
{
	uintptr_t addr = (uintptr_t) dest;
	int len;

	while (nic_recv_req_avail() == 0);
	reg_write64(SIMPLENIC_RECV_REQ, addr);

	// Poll for completion
	while (nic_recv_comp_avail() == 0);
	len = reg_read16(SIMPLENIC_RECV_COMP);

	return len;
}

#define TEST_LEN 128

int main(void)
{
	int i, len;

	for (i = 0; i < TEST_LEN; i++)
		src[i] = i << 12;

	printf("Sending data on loopback NIC\n");

	asm volatile ("fence");
	nic_send(src, TEST_LEN * sizeof(uint64_t));
	len = nic_recv(dst);

	printf("Received %d bytes\n", len);

	for (i = 0; i < TEST_LEN; i++) {
		if (dst[i] != src[i]) {
			printf("Data mismatch @ %d: %lx != %lx\n",
					i, dst[i], src[i]);
			return 1;
		}
	}

	printf("All correct\n");

	return 0;
}
