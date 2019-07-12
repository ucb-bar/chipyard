#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"
#include "blkdev.h"

void blkdev_read(void *addr, unsigned long offset, size_t nsectors)
{
	int req_tag, resp_tag, ntags, i;
	size_t nsectors_per_tag;

	ntags = reg_read8(BLKDEV_NREQUEST);
	nsectors_per_tag = nsectors / ntags;

	printf("sending %d reads\n", ntags);

	for (i = 0; i < ntags; i++) {
		req_tag = blkdev_send_request(
				(unsigned long) addr, offset,
				nsectors_per_tag, 0);
		addr += (nsectors_per_tag << BLKDEV_SECTOR_SHIFT);
		offset += nsectors_per_tag;
	}

	while (reg_read8(BLKDEV_NCOMPLETE) < ntags);

	for (i = 0; i < ntags; i++) {
		resp_tag = reg_read8(BLKDEV_COMPLETE);
		printf("completed read %d\n", resp_tag);
	}
}

void blkdev_write(unsigned long offset, void *addr, size_t nsectors)
{
	int req_tag, resp_tag, ntags, i;
	size_t nsectors_per_tag;

	ntags = reg_read8(BLKDEV_NREQUEST);
	nsectors_per_tag = nsectors / ntags;

	printf("sending %d writes\n", ntags);

	for (i = 0; i < ntags; i++) {
		req_tag = blkdev_send_request(
				(unsigned long) addr, offset,
				nsectors_per_tag, 1);
		addr += (nsectors_per_tag << BLKDEV_SECTOR_SHIFT);
		offset += nsectors_per_tag;
	}

	while (reg_read8(BLKDEV_NCOMPLETE) < ntags);

	for (i = 0; i < ntags; i++) {
		resp_tag = reg_read8(BLKDEV_COMPLETE);
		printf("completed write %d\n", resp_tag);
	}
}

#define TEST_NSECTORS 4
#define TEST_SIZE (TEST_NSECTORS * BLKDEV_SECTOR_SIZE / sizeof(int))

unsigned int test_data[TEST_SIZE];
unsigned int res_data[TEST_SIZE];

int main(void)
{
	unsigned int nsectors = blkdev_nsectors();
	unsigned int max_req_len = blkdev_max_req_len();

	if (nsectors < TEST_NSECTORS) {
		printf("Error: blkdev nsectors not large enough: %u < %u\n",
				nsectors, TEST_NSECTORS);
		return 1;
	}

	if (max_req_len < TEST_NSECTORS) {
		printf("Error: blkdev max_req_len not large enough: %u < %u\n",
				max_req_len, TEST_NSECTORS);
		return 1;
	}

	printf("blkdev: %u sectors %u max request length\n",
			nsectors, max_req_len);

	for (int i = 0; i < TEST_SIZE; i++) {
		test_data[i] = i << 8;
	}

	asm volatile ("fence");

	blkdev_write(0, (void *) test_data, TEST_NSECTORS);
	blkdev_read((void *) res_data, 0, TEST_NSECTORS);

	for (int i = 0; i < TEST_SIZE; i++) {
		if (test_data[i] != res_data[i]) {
			printf("data mismatch at %d: %x != %x\n",
					i, test_data[i], res_data[i]);
			return 1;
		}
	}

	printf("All correct\n");

	return 0;
}
