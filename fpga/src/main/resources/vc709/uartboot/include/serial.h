/*
 * @,@Author: ,: your name
 * @,@Date: ,: 1970-01-01 08:00:00
 * @,@LastEditTime: ,: 2021-01-30 11:45:36
 * @,@LastEditors: ,: Please set LastEditors
 * @,@Description: ,: In User Settings Edit
 * @,@FilePath: ,: /freedom/bootrom/sdboot/include/serial_boot.h
 */

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