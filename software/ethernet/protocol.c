// Shared raw-Ethernet file-transfer protocol helpers.

#include "protocol.h"

// Read a big-endian 16-bit value from protocol bytes.
static uint16_t ethernet_read_u16(const uint8_t *buf) {
  return (uint16_t)(((uint16_t)buf[0] << 8) | (uint16_t)buf[1]);
}

// Read a big-endian 32-bit value from protocol bytes.
static uint32_t ethernet_read_u32(const uint8_t *buf) {
  return ((uint32_t)buf[0] << 24) | ((uint32_t)buf[1] << 16) |
         ((uint32_t)buf[2] << 8) | (uint32_t)buf[3];
}

// Write a big-endian 16-bit value into protocol bytes.
static void ethernet_write_u16(uint8_t *buf, uint16_t value) {
  buf[0] = (uint8_t)(value >> 8);
  buf[1] = (uint8_t)(value & 0xffu);
}

// Write a big-endian 32-bit value into protocol bytes.
static void ethernet_write_u32(uint8_t *buf, uint32_t value) {
  buf[0] = (uint8_t)(value >> 24);
  buf[1] = (uint8_t)((value >> 16) & 0xffu);
  buf[2] = (uint8_t)((value >> 8) & 0xffu);
  buf[3] = (uint8_t)(value & 0xffu);
}

// Compute the protocol CRC32 over `data`.
uint32_t ethernet_crc32(const uint8_t *data, size_t len) {
  uint32_t crc = 0xffffffffu;
  for (size_t i = 0; i < len; i++) {
    crc ^= data[i];
    for (int bit = 0; bit < 8; bit++) {
      uint32_t mask = 0u - (crc & 1u);
      crc = (crc >> 1) ^ (0xedb88320u & mask);
    }
  }
  return ~crc;
}

// Encode one protocol header into wire-order bytes.
int ethernet_encode_header(uint8_t *payload, size_t payload_capacity,
                           const ethernet_packet_header_t *header) {
  if (payload_capacity < kEthernetHeaderLen) {
    return -1;
  }

  payload[0] = 'E';
  payload[1] = 'F';
  payload[2] = 'T';
  payload[3] = '1';
  payload[4] = header->type;
  payload[5] = header->code;
  ethernet_write_u16(&payload[6], 0);
  ethernet_write_u32(&payload[8], header->transfer_id);
  ethernet_write_u32(&payload[12], header->sequence);
  ethernet_write_u32(&payload[16], header->offset);
  ethernet_write_u32(&payload[20], header->length);
  ethernet_write_u32(&payload[24], header->crc32);
  return 0;
}

// Decode one protocol header from wire-order bytes.
int ethernet_decode_header(const uint8_t *payload, size_t payload_len,
                           ethernet_packet_header_t *header) {
  if (payload_len < kEthernetHeaderLen) {
    return -1;
  }
  if (payload[0] != 'E' || payload[1] != 'F' || payload[2] != 'T' || payload[3] != '1') {
    return -1;
  }
  if (ethernet_read_u16(&payload[6]) != 0) {
    return -1;
  }

  header->type = payload[4];
  header->code = payload[5];
  header->transfer_id = ethernet_read_u32(&payload[8]);
  header->sequence = ethernet_read_u32(&payload[12]);
  header->offset = ethernet_read_u32(&payload[16]);
  header->length = ethernet_read_u32(&payload[20]);
  header->crc32 = ethernet_read_u32(&payload[24]);
  return 0;
}
