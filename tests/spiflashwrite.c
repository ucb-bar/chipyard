#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"
#include "spiflash.h"

int main(void)
{
	spiflash_ffmt ffmt;
	ffmt.fields.cmd_en = 1;
	ffmt.fields.addr_len = 4; // Valid options are 3 or 4 for our model
	ffmt.fields.pad_cnt = 0; // Our SPI flash model assumes 8 dummy cycles for fast reads, 0 for slow
	ffmt.fields.cmd_proto = SPIFLASH_PROTO_SINGLE; // Our SPI flash model only supports single-bit commands
	ffmt.fields.addr_proto = SPIFLASH_PROTO_SINGLE; // We support both single and quad
	ffmt.fields.data_proto = SPIFLASH_PROTO_SINGLE; // We support both single and quad
	ffmt.fields.cmd_code = 0x13; // Slow read 4 byte
	ffmt.fields.pad_code = 0x00; // Not used by our model

	// Test that we can read
	printf("Testing SPI flash command 0x13...\n");
	configure_spiflash(ffmt);
	if (test_spiflash(0x0, 0x100, 0)) return 1;

	// 0x02: 3 byte addr, single/single
	printf("Testing SPI flash command 0x02...\n");
	write_spiflash(test_data, test_len, 0x200, 0x02, 3, SPIFLASH_PROTO_SINGLE, SPIFLASH_PROTO_SINGLE);
	if (check_write(test_data, test_len, 0x200)) return 1;

	// 0x32: 3 byte addr, single/quad
	printf("Testing SPI flash command 0x32...\n");
	write_spiflash(test_data, test_len, 0x300, 0x32, 3, SPIFLASH_PROTO_SINGLE, SPIFLASH_PROTO_QUAD);
	if (check_write(test_data, test_len, 0x300)) return 1;

	// 0x38: 3 byte addr, quad/quad
	printf("Testing SPI flash command 0x38...\n");
	write_spiflash(test_data, test_len, 0x400, 0x38, 3, SPIFLASH_PROTO_QUAD, SPIFLASH_PROTO_QUAD);
	if (check_write(test_data, test_len, 0x400)) return 1;

	// 0x12: 4 byte addr, single/single
	printf("Testing SPI flash command 0x12...\n");
	write_spiflash(test_data, test_len, 0x500, 0x12, 4, SPIFLASH_PROTO_SINGLE, SPIFLASH_PROTO_SINGLE);
	if (check_write(test_data, test_len, 0x500)) return 1;

	// 0x34: 4 byte addr, single/quad
	printf("Testing SPI flash command 0x34...\n");
	write_spiflash(test_data, test_len, 0x600, 0x34, 4, SPIFLASH_PROTO_SINGLE, SPIFLASH_PROTO_QUAD);
	if (check_write(test_data, test_len, 0x600)) return 1;

	// 0x3E: 4 byte addr, quad/quad
	printf("Testing SPI flash command 0x3E...\n");
	write_spiflash(test_data, test_len, 0x700, 0x3E, 4, SPIFLASH_PROTO_QUAD, SPIFLASH_PROTO_QUAD);
	if (check_write(test_data, test_len, 0x700)) return 1;

	return 0;
}
