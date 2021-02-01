/*
 * @,@Author: ,: your name
 * @,@Date: ,: 1970-01-01 08:00:00
 * @,@LastEditTime: ,: 2021-01-30 11:48:26
 * @,@LastEditors: ,: Please set LastEditors
 * @,@Description: ,: In User Settings Edit
 * @,@FilePath: ,: /freedom/bootrom/sdboot/crc16.h
 */
#include "include/crc16.h"
 
inline uint16_t crc16_round(uint16_t crc, uint8_t data) {
	crc = (uint8_t)(crc >> 8) | (crc << 8);
	crc ^= data;
	crc ^= (uint8_t)(crc >> 4) & 0xf;
	crc ^= crc << 12;
	crc ^= (crc & 0xff) << 5;
	return crc;
}

uint16_t crc16(uint8_t *q) {
    uint16_t crc = 0;
    for (int i = 0; i < CRC16_LEN; i++) {
        crc = crc16_round(crc, *q++);
    }
	return crc;
}