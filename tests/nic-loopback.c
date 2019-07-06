#include "mmio.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "nic.h"
#include "encoding.h"

#define NPACKETS 10
#define TEST_OFFSET 3
#define TEST_LEN 356
#define ARRAY_LEN 360
#define NTRIALS 3

uint32_t src[NPACKETS][ARRAY_LEN];
uint32_t dst[NPACKETS][ARRAY_LEN];
uint64_t lengths[NPACKETS];

static inline void send_recv()
{
	uint64_t send_packet, recv_addr;
	int ncomps, send_comps_left = NPACKETS, recv_comps_left = NPACKETS;
	int recv_idx = 0;

	for (int i = 0; i < NPACKETS; i++) {
		uint64_t pkt_size = TEST_LEN * sizeof(uint32_t);
		uint64_t src_addr = (uint64_t) &src[i][TEST_OFFSET];
		send_packet = (pkt_size << 48) | src_addr;
		recv_addr = (uint64_t) dst[i];
		reg_write64(SIMPLENIC_SEND_REQ, send_packet);
		reg_write64(SIMPLENIC_RECV_REQ, recv_addr);
	}

	while (send_comps_left > 0 || recv_comps_left > 0) {
		ncomps = nic_send_comp_avail();
		asm volatile ("fence");
		for (int i = 0; i < ncomps; i++)
			reg_read16(SIMPLENIC_SEND_COMP);
		send_comps_left -= ncomps;

		ncomps = nic_recv_comp_avail();
		asm volatile ("fence");
		for (int i = 0; i < ncomps; i++) {
			lengths[recv_idx] = reg_read16(SIMPLENIC_RECV_COMP);
			recv_idx++;
		}
		recv_comps_left -= ncomps;
	}
}

void run_test(void)
{
	unsigned long start, end;
	int i, j;

	memset(dst, 0, sizeof(dst));
	asm volatile ("fence");

	start = rdcycle();
	send_recv();
	end = rdcycle();

	printf("send/recv %lu cycles\n", end - start);

	for (i = 0; i < NPACKETS; i++) {
		if (lengths[i] != TEST_LEN * sizeof(uint32_t)) {
			printf("recv got wrong # bytes\n");
			exit(EXIT_FAILURE);
		}

		for (j = 0; j < TEST_LEN; j++) {
			if (dst[i][j] != src[i][j + TEST_OFFSET]) {
				printf("Data mismatch @ %d, %d: %x != %x\n",
					i, j, dst[i][j], src[i][j + TEST_OFFSET]);
				exit(EXIT_FAILURE);
			}
		}
	}
}

int main(void)
{
	int i, j;

	for (i = 0; i < NPACKETS; i++) {
		for (j = 0; j < ARRAY_LEN; j++)
			src[i][j] = i * ARRAY_LEN + j;
	}

	for (i = 0; i < NTRIALS; i++) {
		printf("Trial %d\n", i);
		run_test();
	}

	printf("All correct\n");

	return 0;
}
