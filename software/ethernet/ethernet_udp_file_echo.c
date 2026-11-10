#include "software/ethernet/ethernet_udp_file_echo.h"

#include "software/ethernet/eth_udp.h"
#include "software/ethernet/ethernet_file_echo.h"
#include "software/ethernet/protocol.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

enum {
  kUdpFileServicePort = 1234,
};

typedef struct {
  bool peer_valid;
  uint32_t peer_ip;
  uint16_t peer_port;
} udp_file_transport_context_t;

/** Echo a non-EFT1 UDP datagram to its sender. */
static int echo_plain_udp(
    const uint8_t *payload, size_t length,
    const eth_udp_metadata_t *metadata) {
  if (length == 0) {
    return 0;
  }
  return eth_udp_send(
      payload, length, metadata->source_ip, kUdpFileServicePort,
      metadata->source_port);
}

static int udp_file_send(
    void *opaque, const uint8_t *packet, size_t length) {
  udp_file_transport_context_t *context =
      (udp_file_transport_context_t *)opaque;
  if (!context->peer_valid) {
    return -1;
  }
  return eth_udp_send(
      packet, length, context->peer_ip, kUdpFileServicePort,
      context->peer_port);
}

static int udp_file_receive(
    void *opaque, uint8_t *packet, size_t capacity, size_t *length) {
  udp_file_transport_context_t *context =
      (udp_file_transport_context_t *)opaque;

  while (true) {
    eth_udp_metadata_t metadata;
    const int received = eth_udp_receive(packet, capacity, &metadata);
    if (received < 0) {
      return -1;
    }
    if (metadata.destination_port != kUdpFileServicePort) {
      continue;
    }

    if (!context->peer_valid) {
      ethernet_packet_header_t header;
      if (ethernet_decode_header(packet, (size_t)received, &header) < 0) {
        if (echo_plain_udp(packet, (size_t)received, &metadata) < 0) {
          return -1;
        }
        continue;
      }
      if (header.type != kEthernetPacketTypeStart) {
        continue;
      }
      context->peer_valid = true;
      context->peer_ip = metadata.source_ip;
      context->peer_port = metadata.source_port;
    } else if (metadata.source_ip != context->peer_ip ||
               metadata.source_port != context->peer_port) {
      continue;
    }

    *length = (size_t)received;
    return 0;
  }
}

int ethernet_udp_file_echo_once(bool verbose) {
  udp_file_transport_context_t context;
  memset(&context, 0, sizeof(context));
  const ethernet_file_transport_t transport = {
      .context = &context,
      .send = udp_file_send,
      .receive = udp_file_receive,
  };
  return ethernet_file_echo_transport_once(&transport, verbose);
}
