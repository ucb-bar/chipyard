#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"

#define BLKDEV_BASE 0x10015000
#define BLKDEV_ADDR BLKDEV_BASE
#define BLKDEV_OFFSET (BLKDEV_BASE + 8)
#define BLKDEV_LEN (BLKDEV_BASE + 12)
#define BLKDEV_WRITE (BLKDEV_BASE + 16)
#define BLKDEV_REQUEST (BLKDEV_BASE + 17)
#define BLKDEV_NREQUEST (BLKDEV_BASE + 18)
#define BLKDEV_COMPLETE (BLKDEV_BASE + 19)
#define BLKDEV_NCOMPLETE (BLKDEV_BASE + 20)
#define BLKDEV_NSECTORS (BLKDEV_BASE + 24)
#define BLKDEV_MAX_REQUEST_LENGTH (BLKDEV_BASE + 28)
#define BLKDEV_SECTOR_SIZE 512
#define BLKDEV_SECTOR_SHIFT 9

size_t blkdev_nsectors(void)
{
	return reg_read32(BLKDEV_NSECTORS);
}

size_t blkdev_max_req_len(void)
{
	return reg_read32(BLKDEV_MAX_REQUEST_LENGTH);
}

void blkdev_read(void *addr, unsigned long offset, size_t nsectors)
{
	int req_tag, resp_tag, ntags, i;
	size_t nsectors_per_tag;

	ntags = reg_read8(BLKDEV_NREQUEST);
	nsectors_per_tag = nsectors / ntags;

	printf("sending %d reads\n", ntags);

	for (i = 0; i < ntags; i++) {
		reg_write64(BLKDEV_ADDR, (unsigned long) addr);
		reg_write32(BLKDEV_OFFSET, offset);
		reg_write32(BLKDEV_LEN, nsectors_per_tag);
		reg_write8(BLKDEV_WRITE, 0);

		req_tag = reg_read8(BLKDEV_REQUEST);
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
		reg_write64(BLKDEV_ADDR, (unsigned long) addr);
		reg_write32(BLKDEV_OFFSET, offset);
		reg_write32(BLKDEV_LEN, nsectors_per_tag);
		reg_write8(BLKDEV_WRITE, 1);

		req_tag = reg_read8(BLKDEV_REQUEST);
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
