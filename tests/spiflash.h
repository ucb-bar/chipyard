#ifndef __SPIFLASH_H__
#define __SPIFLASH_H__

// These are configuration-dependent, but for the unit test we'll use the example config
#define SPIFLASH_BASE_MEM 0x20000000
#define SPIFLASH_BASE_MEM_SIZE 0x10000000

#define SPIFLASH_BASE_CTRL 0x10040000
// Only defining the registers we use; there are more
#define SPIFLASH_OFFS_FLASH_EN 0x60
#define SPIFLASH_OFFS_FFMT 0x64

// SPI flash protocol settings
#define SPIFLASH_PROTO_SINGLE 0
#define SPIFLASH_PROTO_DUAL 1
#define SPIFLASH_PROTO_QUAD 2

typedef union
{
	struct {
		unsigned int cmd_en : 1;
		unsigned int addr_len : 3;
		unsigned int pad_cnt : 4;
		unsigned int cmd_proto : 2;
		unsigned int addr_proto : 2;
		unsigned int data_proto : 2;
		unsigned int : 2;
		unsigned int cmd_code : 8;
		unsigned int pad_code : 8;
	} fields;
	uint32_t bits;
} spiflash_ffmt;

void configure_spiflash(spiflash_ffmt data);
int test_spiflash(uint32_t start, uint32_t size);

#endif /* __SPIFLASH_H__ */
