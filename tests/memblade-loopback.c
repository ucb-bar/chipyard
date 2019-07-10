#include <stdint.h>
#include <stdio.h>

#include "mmio.h"
#include "nic.h"
#include "memblade.h"

#define SPAN_BYTES 256
#define SPAN_WORDS (SPAN_BYTES / sizeof(uint64_t))

uint64_t span_data[SPAN_WORDS];

static inline int send_rmem_request(
		void *src_addr, void *dst_addr,
		uint64_t dstmac, int opcode, uint64_t spanid)
{
	reg_write64(RMEM_CLIENT_SRC_ADDR, (unsigned long) src_addr);
	reg_write64(RMEM_CLIENT_DST_ADDR, (unsigned long) dst_addr);
	reg_write64(RMEM_CLIENT_DSTMAC,   dstmac);
	reg_write8 (RMEM_CLIENT_OPCODE,   opcode);
	reg_write64(RMEM_CLIENT_SPANID,   spanid);

	while (reg_read32(RMEM_CLIENT_NREQ) == 0) {}
	asm volatile ("fence");
	return reg_read32(RMEM_CLIENT_REQ);
}

static inline void wait_response(int xact_id)
{
	for (;;) {
		while (reg_read32(RMEM_CLIENT_NRESP) == 0) {}

		if (reg_read32(RMEM_CLIENT_RESP) == xact_id)
			return;
	}
}

int main(void)
{
	uint64_t mymac;
	uint64_t extdata[9];
	uint64_t word_results[4];
	uint64_t exp_results[4] = {0xBE, 1, 0xDEADB00FL, 4};
	uint64_t spanid = 4;
	int xact_ids[5];

	for (int i = 0; i < SPAN_WORDS; i++)
		span_data[i] = i;

	mymac = nic_macaddr();

	printf("Sending write request\n");

	xact_ids[0] = send_rmem_request(
			span_data, NULL, mymac, MB_OC_SPAN_WRITE, spanid);

	printf("Receiving write response\n");

	wait_response(xact_ids[0]);

	for (int i = 0; i < SPAN_WORDS; i++)
		span_data[i] = 0;

	printf("Sending read request\n");

	xact_ids[0] = send_rmem_request(
			NULL, span_data, mymac, MB_OC_SPAN_READ, spanid);

	printf("Receiving read response\n");

	wait_response(xact_ids[0]);
	asm volatile ("fence");

	printf("Checking read response\n");

	for (int i = 0; i < SPAN_WORDS; i++) {
		if (span_data[i] != i)
			printf("Page data at %d not correct: got %lu\n",
					i, span_data[i]);
	}

	printf("Sending word-sized requests\n");

	spanid = 5;
	extdata[0] = memblade_make_exthead(8, 2);
	extdata[1] = 0xDEADBEEFL;
	xact_ids[0] = send_rmem_request(
			&extdata[0], NULL, mymac, MB_OC_WORD_WRITE, spanid);

	extdata[2] = memblade_make_exthead(9, 0);
	extdata[3] = 5;
	xact_ids[1] = send_rmem_request(
			&extdata[2], &word_results[0], mymac,
			MB_OC_ATOMIC_ADD, spanid);

	extdata[4] = memblade_make_exthead(8, 1);
	extdata[5] = 0xB00F;
	extdata[6] = 0xC3EF;
	xact_ids[2] = send_rmem_request(
			&extdata[4], &word_results[1], mymac,
			MB_OC_COMP_SWAP, spanid);

	extdata[7] = memblade_make_exthead(8, 2);
	xact_ids[3] = send_rmem_request(
			&extdata[7], &word_results[2], mymac,
			MB_OC_WORD_READ, spanid);

	spanid = 4;
	extdata[8] = memblade_make_exthead(32, 3);
	xact_ids[4] = send_rmem_request(
			&extdata[8], &word_results[3], mymac,
			MB_OC_WORD_READ, spanid);

	printf("Receiving word-sized responses\n");

	for (int i = 0; i < 5; i++)
		wait_response(xact_ids[i]);

	for (int i = 0; i < 4; i++) {
		if (word_results[i] != exp_results[i])
			printf("Word result %d incorrect: got %lx\n",
					i, word_results[i]);
	}

	printf("All tests completed successfully\n");

	return 0;
}
