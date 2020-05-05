#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"
#include "spiflash.h"

void configure_spiflash(spiflash_ffmt data)
{
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FLASH_EN, 0);
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FFMT, data.bits);
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FLASH_EN, 1);
}

int test_spiflash(uint32_t start, uint32_t size)
{
	uint32_t i;

	for (i = start; i < (start + size); i += 4)
	{
		uint32_t data = reg_read32(SPIFLASH_BASE_MEM + i);
		uint32_t check = 0xdeadbeef - i;
		if(data != check)
		{
			printf("Error reading address 0x%08x from SPI flash. Got %08x, expected %08x.\n", i, data, check);
			return 1;
		}
	}

	return 0;
}

int main(void)
{
	spiflash_ffmt config;
	config.fields.cmd_en = 1;
	config.fields.addr_len = 4; // Valid options are 3 or 4 for our model
	config.fields.pad_cnt = 0; // Our SPI flash model assumes 8 dummy cycles for fast reads, 0 for slow
	config.fields.cmd_proto = SPIFLASH_PROTO_SINGLE; // Our SPI flash model only supports single-bit commands
	config.fields.addr_proto = SPIFLASH_PROTO_SINGLE; // We support both single and quad
	config.fields.data_proto = SPIFLASH_PROTO_SINGLE; // We support both single and quad
	config.fields.cmd_code = 0x13; // Slow read 4 byte
	config.fields.pad_code = 0x00; // Not used by our model

	printf("Testing SPI Flash command 0x13...\n");
	configure_spiflash(config);
	if (test_spiflash(0x0, 0x100)) return 1;

	// printf("Testing upper address range...\n");
	// if (test_spiflash(SPIFLASH_BASE_MEM - 0x10000, 0x10000)) return 1;

	printf("Testing SPI Flash command 0x03...\n");
	config.fields.cmd_code = 0x03; // Slow read 3 byte address
	config.fields.addr_len = 3; // 3 byte address
	configure_spiflash(config);
	if (test_spiflash(0x0, 0x100)) return 1;

	printf("Testing SPI Flash command 0x0B...\n");
	config.fields.cmd_code = 0x0B; // Fast read 3 byte address
	config.fields.pad_cnt = 8; // Needs to be 8 for fast read
	configure_spiflash(config);
	if (test_spiflash(0x1000, 0x100)) return 1;

	printf("Testing SPI Flash command 0x0C...\n");
	config.fields.cmd_code = 0x0C; // Fast read 4 byte address
	config.fields.addr_len = 4; // 4 byte address
	configure_spiflash(config);
	if (test_spiflash(0x2340, 0x100)) return 1;

	printf("Testing SPI Flash command 0x6C...\n");
	config.fields.cmd_code = 0x6C; // Fast read 4 byte address, quad data
	config.fields.data_proto = SPIFLASH_PROTO_QUAD; // Quad data
	configure_spiflash(config);
	if (test_spiflash(0x410c, 0x100)) return 1;

	printf("Testing SPI Flash command 0x6B...\n");
	config.fields.cmd_code = 0x6B; // Fast read 3 byte address, quad data
	config.fields.addr_len = 3;
	configure_spiflash(config);
	if (test_spiflash(0x5ff8, 0x100)) return 1;

	printf("Testing SPI Flash command 0xEB...\n");
	config.fields.cmd_code = 0xEB; // Fast read 3 byte address, quad data, quad addr
	config.fields.addr_proto = SPIFLASH_PROTO_QUAD;
	configure_spiflash(config);
	if (test_spiflash(0x7c04, 0x100)) return 1;

	printf("Testing SPI Flash command 0xEC...\n");
	config.fields.cmd_code = 0xEC; // Fast read 4 byte address, quad data, quad addr
	config.fields.addr_len = 4;
	configure_spiflash(config);
	if (test_spiflash(0x9000, 0x100)) return 1;

	return 0;

}
