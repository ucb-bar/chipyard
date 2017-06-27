#include "mmio.h"
#include <stdlib.h>
#include <stdio.h>

#define SIMPLENIC_BASE 0x10016000L
#define SIMPLENIC_SEND (SIMPLENIC_BASE + 0)
#define SIMPLENIC_RECV (SIMPLENIC_BASE + 8)

uint64_t src[200];
uint64_t dst[201];

void nic_send(void *data, unsigned long len)
{
	uintptr_t addr = ((uintptr_t) data) & ((1L << 48) - 1);
	unsigned long packet = (len << 48) | addr;

	asm volatile ("fence");
	reg_write64(SIMPLENIC_SEND, packet);
}

void nic_recv(void *dest)
{
	uintptr_t addr = (uintptr_t) dest;
	volatile uint64_t *words = (volatile uint64_t *) dest;

	reg_write64(SIMPLENIC_RECV, addr);

	// Poll for completion
	while (!words[200]);
	asm volatile ("fence");
}

#define TEST_LEN 128

int main(void)
{
	int i;

	for (i = 0; i < TEST_LEN; i++)
		src[i] = i << 12;

	printf("Sending data on loopback NIC\n");

	nic_send(src, TEST_LEN * sizeof(uint64_t));
	nic_recv(dst);

	printf("Received data\n");

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
