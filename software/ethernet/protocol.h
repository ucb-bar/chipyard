// Shared raw-Ethernet file-transfer protocol definitions.

#ifndef SOFTWARE_ETHERNET_PROTOCOL_H_
#define SOFTWARE_ETHERNET_PROTOCOL_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum ethernet_packet_type {
  kEthernetPacketTypeStart = 1,
  kEthernetPacketTypeData = 2,
  kEthernetPacketTypeEnd = 3,
  kEthernetPacketTypeAck = 4,
  kEthernetPacketTypeError = 5,
} ethernet_packet_type_t;

typedef enum ethernet_error_code {
  kEthernetErrorCodeNone = 0,
  kEthernetErrorCodeMalformed = 1,
  kEthernetErrorCodeTooLarge = 2,
  kEthernetErrorCodeSequence = 3,
  kEthernetErrorCodeChecksum = 4,
  kEthernetErrorCodeTimeout = 5,
} ethernet_error_code_t;

enum {
  kEthernetEthertype = 0x88B5,
  kEthernetHeaderLen = 28,
  kEthernetMaxChunkLen = 1400,
  kEthernetMaxFileSize = 64 * 1024,
  kEthernetMacLen = 6,
  kEthernetL2HeaderLen = 14,
  kEthernetMinPayloadLen = 46,
  kEthernetMaxFrameLen = kEthernetL2HeaderLen + kEthernetHeaderLen + kEthernetMaxChunkLen,
};

/**
 * Decoded protocol header.
 *
 * This is an in-memory representation only. Wire payloads are encoded manually
 * as big-endian bytes by #ethernet_encode_header.
 */
typedef struct ethernet_packet_header {
  uint8_t type;
  uint8_t code;
  uint32_t transfer_id;
  uint32_t sequence;
  uint32_t offset;
  uint32_t length;
  uint32_t crc32;
} ethernet_packet_header_t;

/**
 * Compute Ethernet-file-transfer CRC32.
 *
 * @param data Buffer to checksum. May be NULL when `len` is 0.
 * @param len Number of bytes in `data`.
 * @return CRC32 using polynomial 0xEDB88320.
 */
uint32_t ethernet_crc32(const uint8_t *data, size_t len);

/**
 * Encode a protocol header into a payload buffer.
 *
 * @param payload Destination payload bytes.
 * @param payload_capacity Capacity of `payload`.
 * @param header Decoded header fields to encode.
 * @return 0 on success, -1 if `payload_capacity` is too small.
 */
int ethernet_encode_header(uint8_t *payload, size_t payload_capacity,
                           const ethernet_packet_header_t *header);

/**
 * Decode a protocol header from payload bytes.
 *
 * @param payload Source payload bytes.
 * @param payload_len Number of bytes in `payload`.
 * @param[out] header Decoded header fields.
 * @return 0 on success, -1 if the payload is malformed or too short.
 */
int ethernet_decode_header(const uint8_t *payload, size_t payload_len,
                           ethernet_packet_header_t *header);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_PROTOCOL_H_
