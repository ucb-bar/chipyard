#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"

#define BLKDEV_BASE 0x10015000
#define BLKDEV_ADDR BLKDEV_BASE
#define BLKDEV_OFFSET (BLKDEV_BASE + 4)
#define BLKDEV_LEN (BLKDEV_BASE + 8)
#define BLKDEV_WRITE (BLKDEV_BASE + 12)
#define BLKDEV_REQUEST (BLKDEV_BASE + 16)
#define BLKDEV_NREQUEST (BLKDEV_BASE + 20)
#define BLKDEV_COMPLETE (BLKDEV_BASE + 24)
#define BLKDEV_NCOMPLETE (BLKDEV_BASE + 28)
#define BLKDEV_NSECTORS (BLKDEV_BASE + 32)
#define BLKDEV_SECTOR_SIZE 512
#define BLKDEV_SECTOR_SHIFT 9

size_t blkdev_nsectors(void)
{
	return read_reg(BLKDEV_NSECTORS);
}

int blkdev_read(void *addr, unsigned long offset, size_t nsectors)
{
	int req_tag, resp_tag;

	write_reg(BLKDEV_ADDR, (unsigned long) addr);
	write_reg(BLKDEV_OFFSET, offset);
	write_reg(BLKDEV_LEN, nsectors);
	write_reg(BLKDEV_WRITE, 0);

	while (read_reg(BLKDEV_NREQUEST) == 0);
	req_tag = read_reg(BLKDEV_REQUEST);

	while (read_reg(BLKDEV_NCOMPLETE) == 0);

	resp_tag = read_reg(BLKDEV_COMPLETE);
	return (resp_tag == req_tag) ? 0 : -1;
}

int blkdev_write(unsigned long offset, void *addr, size_t nsectors)
{
	int req_tag, resp_tag;

	write_reg(BLKDEV_ADDR, (unsigned long) addr);
	write_reg(BLKDEV_OFFSET, offset);
	write_reg(BLKDEV_LEN, nsectors);
	write_reg(BLKDEV_WRITE, 1);

	req_tag = read_reg(BLKDEV_REQUEST);

	while (read_reg(BLKDEV_NCOMPLETE) == 0);

	resp_tag = read_reg(BLKDEV_COMPLETE);
	return (resp_tag == req_tag) ? 0 : -1;
}

#define NSECTORS 2
#define TEST_SIZE (NSECTORS * BLKDEV_SECTOR_SIZE / sizeof(int))

unsigned int test_data[TEST_SIZE];
unsigned int res_data[TEST_SIZE];

int main(void)
{
	for (int i = 0; i < TEST_SIZE; i++) {
		test_data[i] = i << 8;
	}

	asm volatile ("fence");

	printf("Block device with %ld sectors\n", blkdev_nsectors());

	if (blkdev_write(0, (void *) test_data, NSECTORS)) {
		printf("write error\n");
		return 1;
	}

	if (blkdev_read((void *) res_data, 0, NSECTORS)) {
		printf("read error\n");
		return 1;
	}

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
