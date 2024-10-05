// See LICENSE.Sifive for license details.
#include <stdint.h>

#include <platform.h>

#include "common.h"

#define DEBUG
#include "kprintf.h"

// Total payload in B
#define PAYLOAD_SIZE_B (50 << 20) // default: 30MiB
// A sector is 512 bytes, so (1 << 11) * 512B = 1 MiB
#define SECTOR_SIZE_B 512
// Payload size in # of sectors
#define PAYLOAD_SIZE (PAYLOAD_SIZE_B / SECTOR_SIZE_B)

// The sector at which the BBL partition starts
#define BBL_PARTITION_START_SECTOR 34

#ifndef TL_CLK
#error Must define TL_CLK
#endif

#define F_CLK TL_CLK

//#define MEM_DBG

static volatile uint32_t * const spi = (void *)(SPI_CTRL_ADDR);

static inline uint8_t spi_xfer(uint8_t d)
{
	int32_t r;

	REG32(spi, SPI_REG_TXFIFO) = d;
	do {
		r = REG32(spi, SPI_REG_RXFIFO);
	} while (r < 0);
	return r;
}

static inline uint8_t sd_dummy(void)
{
	return spi_xfer(0xFF);
}

static uint8_t sd_cmd(uint8_t cmd, uint32_t arg, uint8_t crc)
{
	unsigned long n;
	uint8_t r;

	REG32(spi, SPI_REG_CSMODE) = SPI_CSMODE_HOLD;
	sd_dummy();
	spi_xfer(cmd);
	spi_xfer(arg >> 24);
	spi_xfer(arg >> 16);
	spi_xfer(arg >> 8);
	spi_xfer(arg);
	spi_xfer(crc);

	n = 1000;
	do {
		r = sd_dummy();
		if (!(r & 0x80)) {
//			dprintf("sd:cmd: %hx\r\n", r);
			goto done;
		}
	} while (--n > 0);
	kputs("sd_cmd: timeout");
done:
	return r;
}

static inline void sd_cmd_end(void)
{
	sd_dummy();
	REG32(spi, SPI_REG_CSMODE) = SPI_CSMODE_AUTO;
}


static void sd_poweron(void)
{
	long i;
	REG32(spi, SPI_REG_SCKDIV) = (F_CLK / 300000UL);
	REG32(spi, SPI_REG_CSMODE) = SPI_CSMODE_OFF;
	for (i = 10; i > 0; i--) {
		sd_dummy();
	}
	REG32(spi, SPI_REG_CSMODE) = SPI_CSMODE_AUTO;
}

static int sd_cmd0(void)
{
	int rc;
	dputs("CMD0");
	rc = (sd_cmd(0x40, 0, 0x95) != 0x01);
	sd_cmd_end();
	return rc;
}

static int sd_cmd8(void)
{
	int rc;
	dputs("CMD8");
	rc = (sd_cmd(0x48, 0x000001AA, 0x87) != 0x01);
	sd_dummy(); /* command version; reserved */
	sd_dummy(); /* reserved */
	rc |= ((sd_dummy() & 0xF) != 0x1); /* voltage */
	rc |= (sd_dummy() != 0xAA); /* check pattern */
	sd_cmd_end();
	return rc;
}

static void sd_cmd55(void)
{
	sd_cmd(0x77, 0, 0x65);
	sd_cmd_end();
}

static int sd_acmd41(void)
{
	uint8_t r;
	dputs("ACMD41");
	do {
		sd_cmd55();
		r = sd_cmd(0x69, 0x40000000, 0x77); /* HCS = 1 */
	} while (r == 0x01);
	return (r != 0x00);
}

static int sd_cmd58(void)
{
	int rc;
	dputs("CMD58");
	rc = (sd_cmd(0x7A, 0, 0xFD) != 0x00);
	rc |= ((sd_dummy() & 0x80) != 0x80); /* Power up status */
	sd_dummy();
	sd_dummy();
	sd_dummy();
	sd_cmd_end();
	return rc;
}

static int sd_cmd16(void)
{
	int rc;
	dputs("CMD16");
	rc = (sd_cmd(0x50, 0x200, 0x15) != 0x00);
	sd_cmd_end();
	return rc;
}

static uint16_t crc16_round(uint16_t crc, uint8_t data) {
	crc = (uint8_t)(crc >> 8) | (crc << 8);
	crc ^= data;
	crc ^= (uint8_t)(crc >> 4) & 0xf;
	crc ^= crc << 12;
	crc ^= (crc & 0xff) << 5;
	return crc;
}

#define SPIN_SHIFT	6
#define SPIN_UPDATE(i)	(!((i) & ((1 << SPIN_SHIFT)-1)))
#define SPIN_INDEX(i)	(((i) >> SPIN_SHIFT) & 0x3)

static const char spinner[] = { '-', '/', '|', '\\' };

static int copy(void)
{
	volatile uint8_t *p = (void *)(PAYLOAD_DEST);
	long i = PAYLOAD_SIZE;
	int rc = 0;

	dputs("CMD18");

	kprintf("LOADING 0x%xB PAYLOAD\r\n", PAYLOAD_SIZE_B);
	kprintf("LOADING  ");

	// TODO: Speed up SPI freq. (breaks between these two values)
	//REG32(spi, SPI_REG_SCKDIV) = (F_CLK / 16666666UL);
	REG32(spi, SPI_REG_SCKDIV) = (F_CLK / 5000000UL);
	if (sd_cmd(0x52, BBL_PARTITION_START_SECTOR, 0xE1) != 0x00) {
		sd_cmd_end();
		return 1;
	}

	uint32_t dbg_instr = 0;
	uint32_t prt_cnt1 = 0;
	uint32_t prt_cnt2 = 0;
	do {
		uint16_t crc, crc_exp;
		long n;


		crc = 0;
		n = SECTOR_SIZE_B;
		while (sd_dummy() != 0xFE);
		do {
			uint8_t x = sd_dummy();
			*p = x;
			//__asm__ __volatile__ ("fence.i" : : : "memory");

			#ifdef MEM_DBG
				prt_cnt1++;
				if (prt_cnt1 == 4 ) {
					prt_cnt1 = 0;

					/* SD CARD PRINT */
					// //shift in new byte
					dbg_instr = dbg_instr >> 8;
					dbg_instr |= x << 24;
				
					// // Print 1000 first bytes and instructions
					// if (prt_cnt1 == 4 ) {
					// 	prt_cnt2++;
					// 	kprintf("addr: 0x%x - inst: 0x%x\r\n", (p - 3), dbg_instr);
					// }

					/* DDR print */
					// Print instructions from the start of DRAM until the last written address (p)
					// uint32_t instr = 0;
					// for (volatile uint32_t* i = (void *)(PAYLOAD_DEST); i < p; i++){
					// 	uint64_t cycles, cycles2 = 0;

					// 	__asm__ __volatile__ ("csrr %0, mcycle"
					// 							: "=r" (cycles));
					// 	instr = *i;
					// 	__asm__ __volatile__ ("csrr %0, mcycle"
					// 							: "=r" (cycles2));

					// 	uint64_t time = cycles2 - cycles;
					// 	kprintf("Read from addr: 0x%x value: 0x%x - time for access: %x\r\n", i, instr, time);

					// }
					volatile uint32_t* instr = (void *)(PAYLOAD_DEST + 0xC);
					if (*instr != 0x654000ef){
						kprintf("0x8000000C was not 0x654000ef - p at addr: 0x%x\r\n", p);
						kprintf("0x8000000C 	is: 0x%x - p at addr: 0x%x\r\n", instr, p);
						kprintf("Last written instruction was: 0x%x to addr: 0x%x\r\n\r\n\r\n", dbg_instr, p);

						for (volatile uint32_t* i = (void *)(PAYLOAD_DEST); i < 0x80000020; i++){
							uint64_t cycles, cycles2 = 0;

							__asm__ __volatile__ ("csrr %0, mcycle"
													: "=r" (cycles));
							instr = *i;
							__asm__ __volatile__ ("csrr %0, mcycle"
													: "=r" (cycles2));

							uint64_t time = cycles2 - cycles;
							kprintf("Read from addr: 0x%x value: 0x%x - time for access: %x\r\n", i, instr, time);
						}
					}
				}

			#endif
			
			p++;
			crc = crc16_round(crc, x);
		} while (--n > 0);


		crc_exp = ((uint16_t)sd_dummy() << 8);
		crc_exp |= sd_dummy();

		if (crc != crc_exp) {
			kputs("\b- CRC mismatch ");
			rc = 1;
			break;
		}

		if (SPIN_UPDATE(i)) {
			kputc('\b');
			kputc(spinner[SPIN_INDEX(i)]);
		}
	} while (--i > 0);
	sd_cmd_end();

	sd_cmd(0x4C, 0, 0x01);
	sd_cmd_end();
	kputs("\b ");
	return rc;
}


void test_mem() {
	volatile uint8_t *p = (void *)(PAYLOAD_DEST);
	kputs("TEST_MEM");

	kprintf("Writing 0xde to addr: 0x%x\r\n", p);
	*p = (uint8_t)0xde; 
	kprintf("Read 0x%x from addr: 0x%x\r\n", *p, p);
}


int main(void)
{
	REG32(uart, UART_REG_TXCTRL) = UART_TXEN;
	
	test_mem();
	kprintf("Do not disable OOO Processing\n");
	//__asm__ __volatile__ ("csrwi 0x7c1, 0x8");
	
	kputs("INIT");
	sd_poweron();
	if (sd_cmd0() ||
	    sd_cmd8() ||
	    sd_acmd41() ||
	    sd_cmd58() ||
	    sd_cmd16() ||
	    copy()
		) {
		kputs("ERROR");
		return 1;
	}

	uint32_t instr = 0;
	
	for (volatile uint32_t* i = (void *)0x80bbfb88; i < 0x80bbfbff; i++){
		uint64_t cycles, cycles2 = 0;

		//__asm__ __volatile__ ("csrr %0, mcycle"
		//						: "=r" (cycles));
		instr = *i;
		//__asm__ __volatile__ ("csrr %0, mcycle"
		//						: "=r" (cycles2));

		uint64_t time = cycles2 - cycles;
		kprintf("%x; %x\r\n", i, instr);//- time for access: %x\r\n", i, instr, time);
		//__asm__ __volatile__ ("fence.i" : : : "memory");

	}


	kputs("BOOT");

	__asm__ __volatile__ ("fence.i" : : : "memory");

	return 0;
}