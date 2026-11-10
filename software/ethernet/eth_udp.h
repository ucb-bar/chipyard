#ifndef SOFTWARE_ETHERNET_ETH_UDP_H_
#define SOFTWARE_ETHERNET_ETH_UDP_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum {
  kEthUdpMaximumPayload = 1472,
};

/** Runtime network configuration for the UDP offload peripheral. */
typedef struct {
  uint64_t local_mac;
  uint32_t local_ip;
  uint32_t gateway_ip;
  uint32_t subnet_mask;
} eth_udp_config_t;

/** Metadata committed alongside one received UDP payload. */
typedef struct {
  uint32_t source_ip;
  uint32_t destination_ip;
  uint16_t source_port;
  uint16_t destination_port;
  uint16_t checksum;
  uint8_t ttl;
  uint8_t dscp;
  uint8_t ecn;
} eth_udp_metadata_t;

/** Configure the UDP stack, enable DMA, and enable the Ethernet MAC. */
int eth_udp_init(const eth_udp_config_t *config);

/** Send one UDP payload through the hardware offload stack. */
int eth_udp_send(const uint8_t *payload, size_t length,
                 uint32_t destination_ip, uint16_t source_port,
                 uint16_t destination_port);

/**
 * Receive one UDP payload and its committed metadata.
 *
 * This function blocks until a complete valid UDP datagram is available or
 * the DMA reports an error. The DMA cannot cancel a queued receive descriptor,
 * so an idle network is not treated as a receive timeout.
 *
 * @param[out] payload Destination for the received UDP payload.
 * @param capacity Capacity of `payload` in bytes.
 * @param[out] metadata Metadata committed with the received payload.
 * @return Received payload length on success, or -1 on error.
 */
int eth_udp_receive(uint8_t *payload, size_t capacity,
                    eth_udp_metadata_t *metadata);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_ETH_UDP_H_
