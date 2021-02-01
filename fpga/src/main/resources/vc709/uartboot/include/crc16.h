/*
 * @,@Author: ,: your name
 * @,@Date: ,: 1970-01-01 08:00:00
 * @,@LastEditTime: ,: 2021-01-30 12:11:45
 * @,@LastEditors: ,: Please set LastEditors
 * @,@Description: ,: In User Settings Edit
 * @,@FilePath: ,: /freedom/bootrom/sdboot/include/crc16.h
 */
#include <stdint.h>

#define CRC16_BITS 12
#define CRC16_LEN 4096
#define NUM_BLOCKS 1024
#define NAK 0x15
#define ACK 0x06

uint16_t crc16_round(uint16_t crc, uint8_t data);
uint16_t crc16(uint8_t *q);