#ifndef __SERIAL_BOOT_
#define __SERIAL_BOOT_

#include <stdint.h>
#include "platform.h"
#include "crc16.h"
#include "kprintf.h"

#define MAX_CORES 4

typedef enum _cmd_t
{
    UART_CMD_TRANSFER,
    UART_CMD_END
} cmd_t;

typedef struct _package_t
{
    uint8_t *addr;
    long len;
} package_t;

#endif