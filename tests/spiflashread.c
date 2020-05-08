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

	printf("Testing SPI flash command 0x13...\n");
	configure_spiflash(ffmt);
	if (test_spiflash(0x0, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0x03...\n");
	ffmt.fields.cmd_code = 0x03; // Slow read 3 byte address
	ffmt.fields.addr_len = 3; // 3 byte address
	configure_spiflash(ffmt);
	if (test_spiflash(0x0, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0x0B...\n");
	ffmt.fields.cmd_code = 0x0B; // Fast read 3 byte address
	ffmt.fields.pad_cnt = 8; // Needs to be 8 for fast read
	configure_spiflash(ffmt);
	if (test_spiflash(0x1000, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0x0C...\n");
	ffmt.fields.cmd_code = 0x0C; // Fast read 4 byte address
	ffmt.fields.addr_len = 4; // 4 byte address
	configure_spiflash(ffmt);
	if (test_spiflash(0x2340, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0x6C...\n");
	ffmt.fields.cmd_code = 0x6C; // Fast read 4 byte address, quad data
	ffmt.fields.data_proto = SPIFLASH_PROTO_QUAD; // Quad data
	configure_spiflash(ffmt);
	if (test_spiflash(0x410c, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0x6B...\n");
	ffmt.fields.cmd_code = 0x6B; // Fast read 3 byte address, quad data
	ffmt.fields.addr_len = 3;
	configure_spiflash(ffmt);
	if (test_spiflash(0x5ff8, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0xEB...\n");
	ffmt.fields.cmd_code = 0xEB; // Fast read 3 byte address, quad data, quad addr
	ffmt.fields.addr_proto = SPIFLASH_PROTO_QUAD;
	configure_spiflash(ffmt);
	if (test_spiflash(0x7c04, 0x100, 0)) return 1;

	printf("Testing SPI flash command 0xEC...\n");
	ffmt.fields.cmd_code = 0xEC; // Fast read 4 byte address, quad data, quad addr
	ffmt.fields.addr_len = 4;
	configure_spiflash(ffmt);
	if (test_spiflash(0x9000, 0x100, 0)) return 1;

	printf("Testing SPI flash extended range...\n");
	// The provided memory image is only 1MiB, but the model has 16MiB of addressable space
	// This should return 0
	if (test_spiflash(0x100000, 0x100, 1)) return 1;

	// This write should do nothing, so we can just re-test the first test
	printf("Testing that the SPI is not writable...\n");
	write_spiflash(test_data, test_len, 0x0, 0x3E, 4, SPIFLASH_PROTO_QUAD, SPIFLASH_PROTO_QUAD);
	if (test_spiflash(0x0, 0x100, 0)) return 1;

	return 0;

}
