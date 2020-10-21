// See LICENSE for license details.
#ifndef _SDBOOT_KPRINTF_H
#define _SDBOOT_KPRINTF_H

#include <platform.h>
#include <stdint.h>

#define REG32(p, i)	((p)[(i) >> 2])

#ifndef UART_CTRL_ADDR
  #ifndef UART_NUM
    #define UART_NUM 0
  #endif

  #define _CONCAT3(A, B, C) A ## B ## C
  #define _UART_CTRL_ADDR(UART_NUM) _CONCAT3(UART, UART_NUM, _CTRL_ADDR)
  #define UART_CTRL_ADDR _UART_CTRL_ADDR(UART_NUM)
#endif
static volatile uint32_t * const uart = (void *)(UART_CTRL_ADDR);

static inline void kputc(char c)
{
	volatile uint32_t *tx = &REG32(uart, UART_REG_TXFIFO);
#ifdef __riscv_atomic
	int32_t r;
	do {
		__asm__ __volatile__ (
			"amoor.w %0, %2, %1\n"
			: "=r" (r), "+A" (*tx)
			: "r" (c));
	} while (r < 0);
#else
	while ((int32_t)(*tx) < 0);
	*tx = c;
#endif
}

extern void kputs(const char *);
extern void kprintf(const char *, ...);

#ifdef DEBUG
#define dprintf(s, ...)	kprintf((s), ##__VA_ARGS__)
#define dputs(s)	kputs((s))
#else
#define dprintf(s, ...) do { } while (0)
#define dputs(s)	do { } while (0)
#endif

#endif /* _SDBOOT_KPRINTF_H */
