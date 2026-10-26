// Shared receive/store/echo implementation for the raw-Ethernet file-transfer protocol.

#include "software/ethernet/ethernet_file_echo.h"

#include "software/ethernet/eth.h"
#include "software/ethernet/protocol.h"

#include <stdbool.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

static const uint8_t kFpgaMac[kEthernetMacLen] = {0x02, 0x00, 0x00, 0x00, 0x00, 0x01};

static uint8_t file_buffer[kEthernetMaxFileSize];
static uint8_t rx_frame[kEthernetMaxFrameLen];
static uint8_t tx_frame[kEthernetMaxFrameLen];
static bool verbose_logging;
static const ethernet_file_transport_t *active_transport;

// Print one service message when verbose logging is enabled.
static void log_message(const char *format, ...) {
  if (!verbose_logging) {
    return;
  }

  va_list arguments;
  va_start(arguments, format);
  vprintf(format, arguments);
  va_end(arguments);
}

// Build one protocol frame into the shared TX buffer.
static int build_frame(const uint8_t dst[kEthernetMacLen],
                       const ethernet_packet_header_t *header,
                       const uint8_t *data,
                       uint32_t data_len) {
  for (int i = 0; i < kEthernetMacLen; i++) {
    tx_frame[i] = dst[i];
    tx_frame[kEthernetMacLen + i] = kFpgaMac[i];
  }
  tx_frame[12] = (uint8_t)(kEthernetEthertype >> 8);
  tx_frame[13] = (uint8_t)(kEthernetEthertype & 0xffu);

  if (ethernet_encode_header(&tx_frame[kEthernetL2HeaderLen], kEthernetHeaderLen + (size_t)data_len, header) < 0) {
    return -1;
  }
  if (data_len > 0) {
    memcpy(&tx_frame[kEthernetL2HeaderLen + kEthernetHeaderLen], data, data_len);
  }

  uint32_t payload_len = kEthernetHeaderLen + data_len;
  uint32_t padded_payload_len = payload_len;
  if (padded_payload_len < kEthernetMinPayloadLen) {
    padded_payload_len = kEthernetMinPayloadLen;
  }
  for (uint32_t i = payload_len; i < padded_payload_len; i++) {
    tx_frame[kEthernetL2HeaderLen + i] = 0;
  }
  return kEthernetL2HeaderLen + (int)padded_payload_len;
}

// Send one encoded protocol packet to `dst`.
static void send_packet(const uint8_t dst[kEthernetMacLen],
                        const ethernet_packet_header_t *header,
                        const uint8_t *data,
                        uint32_t data_len) {
  if (active_transport != NULL) {
    const size_t packet_len = kEthernetHeaderLen + (size_t)data_len;
    if (ethernet_encode_header(tx_frame, packet_len, header) < 0) {
      return;
    }
    if (data_len != 0) {
      memcpy(tx_frame + kEthernetHeaderLen, data, data_len);
    }
    (void)active_transport->send(
        active_transport->context, tx_frame, packet_len);
  } else {
    int frame_len = build_frame(dst, header, data, data_len);
    if (frame_len > 0) {
      eth_send_frame(tx_frame, frame_len);
    }
  }
}

// Send one ACK or ERROR control packet.
static void send_control(const uint8_t dst[kEthernetMacLen],
                         uint8_t type,
                         uint8_t code,
                         uint32_t transfer_id,
                         uint32_t sequence,
                         uint32_t offset,
                         uint32_t length,
                         uint32_t crc32) {
  ethernet_packet_header_t header = {
      .type = type,
      .code = code,
      .transfer_id = transfer_id,
      .sequence = sequence,
      .offset = offset,
      .length = length,
      .crc32 = crc32,
  };
  send_packet(dst, &header, NULL, 0);
}

// Acknowledge receipt of one protocol packet.
static void send_ack(const uint8_t dst[kEthernetMacLen],
                     uint32_t transfer_id,
                     uint8_t acked_type,
                     uint32_t sequence) {
  send_control(dst, kEthernetPacketTypeAck, acked_type, transfer_id, sequence, 0, 0, 0);
}

// Report a protocol error to the peer.
static void send_error(const uint8_t dst[kEthernetMacLen],
                       uint32_t transfer_id,
                       uint8_t code,
                       uint32_t sequence) {
  send_control(dst, kEthernetPacketTypeError, code, transfer_id, sequence, 0, 0, 0);
}

// Receive the next valid protocol packet from the expected peer.
static int recv_protocol_packet(const uint8_t *expected_src,
                                uint8_t src[kEthernetMacLen],
                                ethernet_packet_header_t *header,
                                const uint8_t **payload,
                                uint32_t *payload_len) {
  while (true) {
    int len;
    size_t transport_length = 0;
    if (active_transport != NULL) {
      if (active_transport->receive(
              active_transport->context, rx_frame, sizeof(rx_frame),
              &transport_length) < 0 || transport_length > sizeof(rx_frame)) {
        continue;
      }
      len = (int)transport_length;
      if (len < kEthernetHeaderLen ||
          ethernet_decode_header(rx_frame, (size_t)len, header) < 0) {
        continue;
      }
      if (src != NULL) {
        memset(src, 0, kEthernetMacLen);
      }
      if (payload != NULL) {
        *payload = rx_frame + kEthernetHeaderLen;
      }
      if (payload_len != NULL) {
        *payload_len = (uint32_t)(len - kEthernetHeaderLen);
      }
      return 0;
    }

    len = eth_recv_frame(rx_frame, sizeof(rx_frame));
    if (len > (int)sizeof(rx_frame)) {
      continue;
    }
    if (len < kEthernetL2HeaderLen + kEthernetHeaderLen) {
      continue;
    }
    uint16_t ethertype = (uint16_t)(((uint16_t)rx_frame[12] << 8) | rx_frame[13]);
    if (ethertype != kEthernetEthertype) {
      continue;
    }
    if (expected_src != NULL && memcmp(&rx_frame[6], expected_src, kEthernetMacLen) != 0) {
      continue;
    }
    if (ethernet_decode_header(&rx_frame[kEthernetL2HeaderLen], (size_t)(len - kEthernetL2HeaderLen), header) < 0) {
      continue;
    }

    if (src != NULL) {
      memcpy(src, &rx_frame[6], kEthernetMacLen);
    }
    if (payload != NULL) {
      *payload = &rx_frame[kEthernetL2HeaderLen + kEthernetHeaderLen];
    }
    if (payload_len != NULL) {
      *payload_len = (uint32_t)(len - kEthernetL2HeaderLen - kEthernetHeaderLen);
    }
    return 0;
  }
}

// Wait until the peer acknowledges the selected packet.
static int wait_ack(const uint8_t peer_mac[kEthernetMacLen],
                    uint32_t transfer_id,
                    uint8_t acked_type,
                    uint32_t sequence) {
  while (true) {
    ethernet_packet_header_t header;
    (void)recv_protocol_packet(peer_mac, NULL, &header, NULL, NULL);
    if (header.transfer_id != transfer_id) {
      continue;
    }
    if (header.type == kEthernetPacketTypeAck && header.code == acked_type &&
        header.sequence == sequence) {
      return 0;
    }
    if (header.type == kEthernetPacketTypeError) {
      log_message("[ethernet] peer error code=%u seq=%lu\n", header.code,
                  (unsigned long)header.sequence);
      return -1;
    }
  }
}

// Detect a retransmitted DATA packet that was already stored and ACKed.
static int is_duplicate_data_packet(const ethernet_packet_header_t *header,
                                    const uint8_t *payload,
                                    uint32_t payload_len,
                                    uint32_t expected_sequence,
                                    uint32_t received,
                                    uint32_t file_size) {
  if (expected_sequence == 0 || header->sequence != expected_sequence - 1) {
    return 0;
  }
  if (header->length > kEthernetMaxChunkLen || header->length > payload_len) {
    return 0;
  }
  if (header->offset > file_size || header->length > file_size - header->offset) {
    return 0;
  }
  if (header->offset + header->length != received) {
    return 0;
  }
  if (ethernet_crc32(payload, header->length) != header->crc32) {
    return 0;
  }
  if (header->length > 0 &&
      memcmp(&file_buffer[header->offset], payload, header->length) != 0) {
    return 0;
  }
  return 1;
}

// Receive and verify one complete file transfer into `file_buffer`.
static int receive_file(uint8_t peer_mac[kEthernetMacLen],
                        uint32_t *transfer_id,
                        uint32_t *file_size,
                        uint32_t *file_crc32) {
  log_message("[ethernet] waiting for START\n");

  while (true) {
    uint8_t src[kEthernetMacLen];
    ethernet_packet_header_t header;
    (void)recv_protocol_packet(NULL, src, &header, NULL, NULL);

    if (header.type != kEthernetPacketTypeStart) {
      continue;
    }

    memcpy(peer_mac, src, kEthernetMacLen);
    *transfer_id = header.transfer_id;
    *file_size = header.length;
    *file_crc32 = header.crc32;

    if (*file_size > kEthernetMaxFileSize) {
      log_message("[ethernet] reject oversized file size=%lu\n", (unsigned long)*file_size);
      send_error(peer_mac, *transfer_id, kEthernetErrorCodeTooLarge, header.sequence);
      continue;
    }

    send_ack(peer_mac, *transfer_id, kEthernetPacketTypeStart, header.sequence);
    log_message("[ethernet] START id=%lu size=%lu crc=0x%08lx\n",
                (unsigned long)*transfer_id, (unsigned long)*file_size,
                (unsigned long)*file_crc32);
    break;
  }

  uint32_t expected_sequence = 0;
  uint32_t received = 0;
  while (true) {
    const uint8_t *payload = NULL;
    uint32_t payload_len = 0;
    ethernet_packet_header_t header;
    (void)recv_protocol_packet(peer_mac, NULL, &header, &payload, &payload_len);

    if (header.transfer_id != *transfer_id) {
      continue;
    }

    if (header.type == kEthernetPacketTypeStart) {
      send_ack(peer_mac, *transfer_id, kEthernetPacketTypeStart, header.sequence);
    } else if (header.type == kEthernetPacketTypeData) {
      if (is_duplicate_data_packet(&header, payload, payload_len, expected_sequence,
                                   received, *file_size)) {
        send_ack(peer_mac, *transfer_id, kEthernetPacketTypeData, header.sequence);
        continue;
      }

      if (header.sequence != expected_sequence || header.offset != received ||
          header.length > kEthernetMaxChunkLen || header.length > payload_len ||
          header.length > *file_size - received) {
        log_message("[ethernet] DATA sequence/range error seq=%lu expected=%lu "
                    "offset=%lu received=%lu len=%lu\n",
                    (unsigned long)header.sequence, (unsigned long)expected_sequence,
                    (unsigned long)header.offset, (unsigned long)received,
                    (unsigned long)header.length);
        send_error(peer_mac, *transfer_id, kEthernetErrorCodeSequence, header.sequence);
        return -1;
      }

      uint32_t chunk_crc32 = ethernet_crc32(payload, header.length);
      if (chunk_crc32 != header.crc32) {
        log_message(
            "[ethernet] DATA checksum error seq=%lu got=0x%08lx expected=0x%08lx\n",
            (unsigned long)header.sequence, (unsigned long)chunk_crc32,
            (unsigned long)header.crc32);
        send_error(peer_mac, *transfer_id, kEthernetErrorCodeChecksum, header.sequence);
        return -1;
      }

      if (header.length > 0) {
        memcpy(&file_buffer[received], payload, header.length);
      }
      received += header.length;
      send_ack(peer_mac, *transfer_id, kEthernetPacketTypeData, header.sequence);
      expected_sequence++;
    } else if (header.type == kEthernetPacketTypeEnd) {
      uint32_t actual_crc32 = ethernet_crc32(file_buffer, received);
      if (header.sequence != expected_sequence || header.offset != received ||
          header.length != *file_size || received != *file_size ||
          header.crc32 != *file_crc32 || actual_crc32 != *file_crc32) {
        log_message("[ethernet] END verification error size=%lu received=%lu "
                    "got_crc=0x%08lx expected_crc=0x%08lx\n",
                    (unsigned long)*file_size, (unsigned long)received,
                    (unsigned long)actual_crc32, (unsigned long)*file_crc32);
        send_error(peer_mac, *transfer_id, kEthernetErrorCodeChecksum, header.sequence);
        return -1;
      }

      send_ack(peer_mac, *transfer_id, kEthernetPacketTypeEnd, header.sequence);
      log_message("[ethernet] receive complete size=%lu crc=0x%08lx\n", (unsigned long)received, (unsigned long)actual_crc32);
      return 0;
    } else if (header.type == kEthernetPacketTypeError) {
      log_message("[ethernet] peer error while receiving code=%u seq=%lu\n", header.code, (unsigned long)header.sequence);
      return -1;
    } else {
      send_error(peer_mac, *transfer_id, kEthernetErrorCodeMalformed, header.sequence);
      return -1;
    }
  }
}

// Echo the stored file back to the peer with START/DATA/END framing.
static int send_file(const uint8_t peer_mac[kEthernetMacLen],
                     uint32_t transfer_id,
                     uint32_t file_size,
                     uint32_t file_crc32) {
  ethernet_packet_header_t header = {
      .type = kEthernetPacketTypeStart,
      .code = 0,
      .transfer_id = transfer_id,
      .sequence = 0,
      .offset = 0,
      .length = file_size,
      .crc32 = file_crc32,
  };
  log_message("[ethernet] echo START size=%lu crc=0x%08lx\n", (unsigned long)file_size, (unsigned long)file_crc32);
  send_packet(peer_mac, &header, NULL, 0);
  if (wait_ack(peer_mac, transfer_id, kEthernetPacketTypeStart, 0) < 0) {
    return -1;
  }

  uint32_t sequence = 0;
  uint32_t offset = 0;
  while (offset < file_size) {
    uint32_t chunk_len = file_size - offset;
    if (chunk_len > kEthernetMaxChunkLen) {
      chunk_len = kEthernetMaxChunkLen;
    }

    header.type = kEthernetPacketTypeData;
    header.code = 0;
    header.sequence = sequence;
    header.offset = offset;
    header.length = chunk_len;
    header.crc32 = ethernet_crc32(&file_buffer[offset], chunk_len);
    send_packet(peer_mac, &header, &file_buffer[offset], chunk_len);
    if (wait_ack(peer_mac, transfer_id, kEthernetPacketTypeData, sequence) < 0) {
      return -1;
    }

    offset += chunk_len;
    sequence++;
  }

  header.type = kEthernetPacketTypeEnd;
  header.code = 0;
  header.sequence = sequence;
  header.offset = file_size;
  header.length = file_size;
  header.crc32 = file_crc32;
  send_packet(peer_mac, &header, NULL, 0);
  if (wait_ack(peer_mac, transfer_id, kEthernetPacketTypeEnd, sequence) < 0) {
    return -1;
  }

  log_message("[ethernet] echo complete size=%lu\n", (unsigned long)file_size);
  return 0;
}

int ethernet_file_echo_once(bool verbose) {
  active_transport = NULL;
  return ethernet_file_echo_transport_once(NULL, verbose);
}

int ethernet_file_echo_transport_once(
    const ethernet_file_transport_t *transport, bool verbose) {
  if (transport != NULL &&
      (transport->send == NULL || transport->receive == NULL)) {
    return -1;
  }
  active_transport = transport;
  verbose_logging = verbose;
  uint8_t peer_mac[kEthernetMacLen];
  uint32_t transfer_id = 0;
  uint32_t file_size = 0;
  uint32_t file_crc32 = 0;

  if (receive_file(peer_mac, &transfer_id, &file_size, &file_crc32) < 0) {
    active_transport = NULL;
    return -1;
  }
  const int result = send_file(
      peer_mac, transfer_id, file_size, file_crc32);
  active_transport = NULL;
  return result;
}
