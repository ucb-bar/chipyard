#include <stdio.h>
#include <stdlib.h>

#include "mmio.h"
#include "blkdev.h"

#define SECTOR_WORDS (BLKDEV_SECTOR_SIZE / sizeof(uint64_t))
#define TEST_SECTORS 16

unsigned long sector_buf[SECTOR_WORDS];

void write_sector(unsigned int secnum)
{
	int req_tag, resp_tag;

	for (int i = 0; i < SECTOR_WORDS; i++)
		sector_buf[i] = (secnum << 6) | i;

	while (reg_read8(BLKDEV_NREQUEST) == 0);
	req_tag = blkdev_send_request((unsigned long) sector_buf, secnum, 1, 1);
	while (reg_read8(BLKDEV_NCOMPLETE) == 0);
	resp_tag = reg_read8(BLKDEV_COMPLETE);

	if (req_tag != resp_tag) {
		printf("Response tag %d does not match request tag %d\n",
				req_tag, resp_tag);
		exit(EXIT_FAILURE);
	}
}

void check_sector(unsigned int secnum)
{
	int req_tag, resp_tag;

	while (reg_read8(BLKDEV_NREQUEST) == 0);
	req_tag = blkdev_send_request((unsigned long) sector_buf, secnum, 1, 0);
	while (reg_read8(BLKDEV_NCOMPLETE) == 0);
	resp_tag = reg_read8(BLKDEV_COMPLETE);

	if (req_tag != resp_tag) {
		printf("Response tag %d does not match request tag %d\n",
				req_tag, resp_tag);
		exit(EXIT_FAILURE);
	}

	for (int i = 0; i < SECTOR_WORDS; i++) {
		unsigned long expected = (secnum << 6) | i;
		unsigned long actual = sector_buf[i];
		if (actual != expected) {
			printf("Word %d in sector %x does not match expected\n",
					i, secnum);
			printf("Expected %lx, got %lx\n",
					expected, actual);
			exit(EXIT_FAILURE);
		}
	}
}

int main(void)
{
	unsigned int nsectors = blkdev_nsectors();
	unsigned int stride = nsectors / TEST_SECTORS;

	printf("Writing %u of %u sectors\n", TEST_SECTORS, nsectors);

	for (int i = 0; i < TEST_SECTORS; i++) {
		int sector = i * stride;
		write_sector(sector);
	}

	printf("Checking sectors\n", nsectors);

	for (int i = 0; i < TEST_SECTORS; i++) {
		int sector = i * stride;
		check_sector(sector);
	}

	return 0;
}
