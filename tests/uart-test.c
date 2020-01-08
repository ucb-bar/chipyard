#include "mmio.h"

#define UART_BASE 0x54000000L
#define UART_REG_TXFIFO (UART_BASE + 0x00)
#define UART_REG_RXFIFO (UART_BASE + 0x04)
#define UART_REG_TXCTRL (UART_BASE + 0x08)
#define UART_REG_TXMARK (UART_BASE + 0x0a)
#define UART_REG_RXCTRL (UART_BASE + 0x0c)
#define UART_REG_RXMARK (UART_BASE + 0x0e)
#define UART_REG_IE     (UART_BASE + 0x10)
#define UART_REG_IP     (UART_BASE + 0x14)
#define UART_REG_DIV    (UART_BASE + 0x18)

void uart_tx_wait(void)
{
	while ((int32_t) reg_read32(UART_REG_TXFIFO) < 0);
	asm volatile ("fence");
}

void uart_putchar(char ch)
{
	uart_tx_wait();
	reg_write32(UART_REG_TXFIFO, ch);
}

void uart_putstr(const char *str)
{
	int i;

	for (i = 0; str[i] != '\0'; i++)
		uart_putchar(str[i]);

	uart_tx_wait();
}

int main(void)
{
	reg_write64(UART_REG_TXCTRL, 1);
	reg_write64(UART_REG_DIV,  813);

	uart_putstr("Hello, World!\n");

	for (;;) {}

	return 0;
}
