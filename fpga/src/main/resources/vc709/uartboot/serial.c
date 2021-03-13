#include "./include/serial.h"

static int read_block(uint8_t *q)
{
    int retry = -1;
    char cmd = NAK;

    uint16_t crc_exp;
    do {
        retry++;
        uint8_t *p = q;
        // receive file and crc
        kread((char *)p, CRC16_LEN);
        kread((char *)&crc_exp, 2);
        // ACK/NAK
        cmd = ((crc16(p) == crc_exp) ? ACK : NAK);
        kwrite(&cmd, 1);
    } while (cmd != ACK);

    return retry;
}

static int read(uint8_t *addr, long len)
{
    uint8_t *p = addr;
    int retry = 0;
    int n_blocks = len >> CRC16_BITS;
    n_blocks += (((len % CRC16_LEN) == 0)? 0 : 1);
    for (int i = 0; i < n_blocks; i++, p += CRC16_LEN) {
		retry += read_block(p);
    }
    return retry;
}

static void session(void)
{
    cmd_t cmd;
    while (cmd != UART_CMD_END) {
        kread((char *)&cmd, sizeof(cmd_t));
        if (cmd == UART_CMD_TRANSFER) {
            package_t package;
            kread((char*)&package, sizeof(package_t));
            read(package.addr, package.len);
        }
    }
}

int main(void)
{
	REG32(uart, UART_REG_TXCTRL) = UART_TXEN;
    REG32(uart, UART_REG_RXCTRL) = UART_RXEN;

	kputs("BOOT INIT");
    
    session();

	kputs("BOOT END");

	REG32(uart, UART_REG_TXCTRL) &= ~UART_TXEN;
    REG32(uart, UART_REG_RXCTRL) &= ~UART_RXEN;

	__asm__ __volatile__ ("fence.i" : : : "memory");
	return 0;
}
