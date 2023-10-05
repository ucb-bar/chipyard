#ifndef __SPIFLASH_H__
#define __SPIFLASH_H__

// These are configuration-dependent, but for the unit test we'll use the example config
#define SPIFLASH_BASE_MEM 0x20000000
#define SPIFLASH_BASE_MEM_SIZE 0x10000000

#define SPIFLASH_BASE_CTRL 0x10030000
// Only defining the registers we use; there are more
// Software control
#define SPIFLASH_OFFS_CSMODE 0x18
#define SPIFLASH_OFFS_FMT 0x40
#define SPIFLASH_OFFS_TXDATA 0x48
#define SPIFLASH_OFFS_RXDATA 0x4c
// Hardware state machine control
#define SPIFLASH_OFFS_FLASH_EN 0x60
#define SPIFLASH_OFFS_FFMT 0x64

// chip select modes
#define CSMODE_AUTO 0
#define CSMODE_HOLD 2
#define CSMODE_OFF 3

// SPI flash protocol settings
#define SPIFLASH_PROTO_SINGLE 0
#define SPIFLASH_PROTO_DUAL 1
#define SPIFLASH_PROTO_QUAD 2

// SPI flash IO settings
#define SPIFLASH_IODIR_RX 0
#define SPIFLASH_IODIR_TX 1

// SPI flash endianness settings
#define SPIFLASH_ENDIAN_MSB 0
#define SPIFLASH_ENDIAN_LSB 1

static uint8_t test_data[] = {0x13,0x37,0x00,0xff,0xaa,0x55,0xfa,0xce,0x0f,0xf0,0x01,0x23,0x45,0x67,0x89,0xab,0xcd,0xef};
static uint8_t test_len = 16;

typedef union
{
	struct {
		unsigned int proto : 2;
		unsigned int endian : 1;
		unsigned int iodir : 1;
		unsigned int : 12;
		unsigned int len : 4;
		unsigned int : 12;
	} fields;
	uint32_t bits;
} spi_fmt;

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

// send something to the SPI TX
void spi_data_write(uint8_t data)
{
	while (reg_read32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_TXDATA) >= 0x80000000);
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_TXDATA, (uint32_t)data);
}

// configure the hardware flash controller
void configure_spiflash(spiflash_ffmt data)
{
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FLASH_EN, 0);
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FFMT, data.bits);
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FLASH_EN, 1);
}

// write some data to the flash using software (there is no hardware write controller)
void write_spiflash(uint8_t *data, uint32_t len, uint32_t addr, uint8_t cmd, uint8_t abytes, uint8_t aproto, uint8_t dproto)
{

	spi_fmt fmt;
	fmt.fields.proto = SPIFLASH_PROTO_SINGLE;
	fmt.fields.endian = SPIFLASH_ENDIAN_MSB;
	fmt.fields.iodir = SPIFLASH_IODIR_TX;
	fmt.fields.len = 8;

	uint32_t i;

	// Need to be out of flash mode
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FLASH_EN, 0);

	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FMT, fmt.bits);
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_CSMODE, CSMODE_HOLD);

	spi_data_write(cmd);

	// need to wait a bit to flush the tx queue before changing fmt
	for(i = 0; i < 0x100; i++) asm volatile ("nop");

	fmt.fields.proto = aproto;
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FMT, fmt.bits);

	for (i = abytes; i > 0; i--)
	{
		spi_data_write((uint8_t)(addr >> (i*8-8)));
	}

	// need to wait a bit to flush the tx queue before changing fmt
	for(i = 0; i < 0x100; i++) asm volatile ("nop");

	fmt.fields.proto = dproto;
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FMT, fmt.bits);

	for (i = 0; i < len; i++)
	{
		spi_data_write(data[i]);
	}

	// need to wait a bit to flush the tx queue before deasserting CS
	for(i = 0; i < 0x100; i++) asm volatile ("nop");

	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_CSMODE, CSMODE_OFF);

	// go back into flash read mode
	reg_write32(SPIFLASH_BASE_CTRL + SPIFLASH_OFFS_FLASH_EN, 1);

}

// test that a large chunk of memory contains (0xdeadbeef - address) or 0
int test_spiflash(uint32_t start, uint32_t size, uint8_t zero)
{
	uint32_t i;

	for (i = start; i < (start + size); i += 4)
	{
		uint32_t data = reg_read32(SPIFLASH_BASE_MEM + i);
		uint32_t check = 0;
		if (!zero) check = 0xdeadbeef - i;
		if(data != check)
		{
			printf("Error reading address 0x%08x from SPI flash. Got 0x%08x, expected 0x%08x.\n", i, data, check);
			return 1;
		}
	}

	return 0;
}

// this is a variant of test_spiflash that only tests a small array of values
int check_write(uint8_t *check, uint32_t len, uint32_t addr)
{
	uint32_t i;
	for (i = 0; i < len; i += 4)
	{
		uint32_t data = reg_read32(SPIFLASH_BASE_MEM + addr + i);
		uint32_t check32 = ((uint32_t *)check)[i/4];
		if(check32 != data)
		{
			printf("Error reading address 0x%08x from SPI flash. Got 0x%02x, expected 0x%02x.\n", i + addr, data, check32);
			return 1;
		}
	}

	return 0;
}

#endif /* __SPIFLASH_H__ */
