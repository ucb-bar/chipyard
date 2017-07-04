#include "mmio.h"
#include <stdlib.h>
#include <stdio.h>

#include "nic.h"

uint64_t src[200];
uint64_t dst[200];

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
