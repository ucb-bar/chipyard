#include "software/ethernet/eth_udp.h"

#include "software/ethernet/eth.h"

#include <stdio.h>

#define ETH_UDP_RX_INFO (ETH_FRONTEND_BASE + 0x00)
#define ETH_UDP_RX_IPS (ETH_FRONTEND_BASE + 0x08)
#define ETH_UDP_RX_META (ETH_FRONTEND_BASE + 0x10)
#define ETH_UDP_RX_POP (ETH_FRONTEND_BASE + 0x18)
#define ETH_UDP_RX_COUNT (ETH_FRONTEND_BASE + 0x20)
#define ETH_UDP_TX_IP (ETH_FRONTEND_BASE + 0x30)
#define ETH_UDP_TX_PORTS (ETH_FRONTEND_BASE + 0x38)
#define ETH_UDP_TX_INFO (ETH_FRONTEND_BASE + 0x40)
#define ETH_UDP_TX_SUBMIT (ETH_FRONTEND_BASE + 0x48)
#define ETH_UDP_TX_SPACE (ETH_FRONTEND_BASE + 0x50)
#define ETH_UDP_CAPABILITIES (ETH_FRONTEND_BASE + 0x58)
#define ETH_UDP_LOCAL_MAC (ETH_FRONTEND_BASE + 0x60)
#define ETH_UDP_LOCAL_IP (ETH_FRONTEND_BASE + 0x68)
#define ETH_UDP_GATEWAY_IP (ETH_FRONTEND_BASE + 0x70)
#define ETH_UDP_SUBNET_MASK (ETH_FRONTEND_BASE + 0x78)
#define ETH_UDP_CLEAR_ARP (ETH_FRONTEND_BASE + 0x80)

#define ETH_UDP_RX_VALID (1u << 16)
#define ETH_UDP_RX_LENGTH_MASK 0xffffu

int eth_udp_init(const eth_udp_config_t *config) {
  if (config == NULL || config->local_mac >= (UINT64_C(1) << 48)) {
    return -1;
  }

  eth_w64(ETH_UDP_LOCAL_MAC, config->local_mac);
  eth_w64(ETH_UDP_LOCAL_IP, config->local_ip);
  eth_w64(ETH_UDP_GATEWAY_IP, config->gateway_ip);
  eth_w64(ETH_UDP_SUBNET_MASK, config->subnet_mask);
  eth_w64(ETH_UDP_CLEAR_ARP, 1);
  eth_init();

  const uint64_t maximum_payload = eth_r64(ETH_UDP_CAPABILITIES) & 0xffffu;
  if (maximum_payload != kEthUdpMaximumPayload) {
    printf("[eth-udp] unsupported maximum payload %lu\n",
           (unsigned long)maximum_payload);
    return -1;
  }
  return 0;
}

int eth_udp_send(const uint8_t *payload, size_t length,
                 uint32_t destination_ip, uint16_t source_port,
                 uint16_t destination_port) {
  if (length == 0 || length > kEthUdpMaximumPayload ||
      eth_dma_prepare_tx_bytes(
          payload, (int)length, kEthUdpMaximumPayload) < 0) {
    return -1;
  }

  for (long spin = 0; eth_r64(ETH_UDP_TX_SPACE) == 0; spin++) {
    if (spin >= ETH_DMA_POLL_SPINS) {
      printf("[eth-udp] TX descriptor queue full\n");
      return -1;
    }
  }

  eth_w64(ETH_UDP_TX_IP, destination_ip);
  eth_w64(ETH_UDP_TX_PORTS,
          ((uint64_t)destination_port << 16) | source_port);
  eth_w64(ETH_UDP_TX_INFO, ((uint64_t)64 << 16) | length);
  eth_w64(ETH_UDP_TX_SUBMIT, 1);
  return eth_dma_send_prepared_bytes((int)length);
}

int eth_udp_receive(uint8_t *payload, size_t capacity,
                    eth_udp_metadata_t *metadata) {
  if (payload == NULL || metadata == NULL) {
    return -1;
  }

  const int length = eth_dma_receive_with_descriptor(
      payload, (int)capacity, ETH_DMA_WAIT_FOREVER, ETH_UDP_RX_COUNT,
      ETH_UDP_RX_INFO, ETH_UDP_RX_VALID, 0);
  if (length < 0) {
    return -1;
  }

  const uint64_t info = eth_r64(ETH_UDP_RX_INFO);
  const uint64_t ips = eth_r64(ETH_UDP_RX_IPS);
  const uint64_t fields = eth_r64(ETH_UDP_RX_META);
  if ((info & ETH_UDP_RX_VALID) == 0 ||
      (info & ETH_UDP_RX_LENGTH_MASK) != (uint64_t)length) {
    printf("[eth-udp] RX descriptor/DMA length mismatch\n");
    eth_w64(ETH_UDP_RX_POP, 1);
    return -1;
  }

  metadata->source_ip = (uint32_t)ips;
  metadata->destination_ip = (uint32_t)(ips >> 32);
  metadata->source_port = (uint16_t)fields;
  metadata->destination_port = (uint16_t)(fields >> 16);
  metadata->checksum = (uint16_t)(fields >> 32);
  metadata->ttl = (uint8_t)(fields >> 48);
  metadata->dscp = (uint8_t)((fields >> 56) & 0x3fu);
  metadata->ecn = (uint8_t)(fields >> 62);
  eth_w64(ETH_UDP_RX_POP, 1);

  if ((size_t)length > capacity) {
    printf("[eth-udp] RX payload length %d exceeds capacity %lu\n",
           length, (unsigned long)capacity);
    return -1;
  }
  return length;
}
