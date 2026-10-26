// Self-checking Ethernet DMA loopback test for simulation.

#include "software/ethernet/eth.h"

#include <stdint.h>
#include <stdio.h>

enum {
  kEthertype = 0x88B5,
  kMaxFrameLen = ETH_MAX_FRAME,
};

static const uint8_t kDstMac[6] = {0x02, 0x00, 0x00, 0x00, 0x00, 0x02};
static const uint8_t kSrcMac[6] = {0x02, 0x00, 0x00, 0x00, 0x00, 0x01};
static const int kFrameLengths[] = {
    60, 60, 61, 67, 1514, 2049, 3000, 4095, 4096};

static uint8_t tx_frame[kMaxFrameLen];
static uint8_t rx_frame[kMaxFrameLen];

/**
 * Case zero is the original 60-byte payload pattern.
 * The remaining cases use
 * a length- and sequence-dependent pattern that exposes padding, trimming,
 * stale-byte, and beat-boundary errors.
 */
static void build_test_frame(int case_index, int length) {
  for (int i = 0; i < 6; i++) {
    tx_frame[i] = kDstMac[i];
    tx_frame[6 + i] = kSrcMac[i];
  }
  tx_frame[12] = (uint8_t)(kEthertype >> 8);
  tx_frame[13] = (uint8_t)(kEthertype & 0xffu);

  if (case_index == 0) {
    for (int i = 14; i < length; i++) {
      tx_frame[i] = (uint8_t)(0xa0 + i - 14);
    }
    return;
  }

  tx_frame[14] = (uint8_t)case_index;
  tx_frame[15] = (uint8_t)(case_index >> 8);
  for (int i = 16; i < length; i++) {
    tx_frame[i] = (uint8_t)(0x31u * (uint32_t)case_index +
                            0x9du * (uint32_t)i + (uint32_t)length);
  }
}

static int verify_frame(int case_index, int tx_len, int rx_len) {
  if (rx_len != tx_len) {
    printf("[simloop] FAIL case=%d RX_LEN=%d expected=%d\n",
           case_index, rx_len, tx_len);
    return -1;
  }
  for (int i = 0; i < tx_len; i++) {
    if (rx_frame[i] != tx_frame[i]) {
      printf("[simloop] FAIL case=%d byte=%d got=0x%02x expected=0x%02x\n",
             case_index, i, rx_frame[i], tx_frame[i]);
      return -1;
    }
  }
  return 0;
}

int main(void) {
  const uint32_t error_bits = ETH_ST_RX_ERR_BAD_FRAME | ETH_ST_RX_ERR_BAD_FCS |
                              ETH_ST_RX_FIFO_OVERFLOW | ETH_ST_RX_FIFO_BAD_FR;

  printf("[simloop] init DMA/frontend/MAC\n");
  eth_init();
  printf("[simloop] frontend=0x%lx status=0x%lx\n",
         (unsigned long)eth_r64(ETH_FRONTEND_INFO),
         (unsigned long)eth_status());

  for (int case_index = 0;
       case_index < (int)(sizeof(kFrameLengths) / sizeof(kFrameLengths[0]));
       case_index++) {
    int tx_len = kFrameLengths[case_index];
    int rx_len = -1;
    uint32_t status;

    build_test_frame(case_index, tx_len);
    printf("[simloop] case=%d tx_len=%d\n", case_index, tx_len);
    if (eth_send_frame_bounded(tx_frame, tx_len) < 0) {
      printf("[simloop] FAIL case=%d TX DMA\n", case_index);
      printf("LOOPBACK FAIL\n");
      return 1;
    }
    if (eth_recv_frame_bounded(rx_frame, (int)sizeof(rx_frame), &rx_len) < 0) {
      printf("[simloop] FAIL case=%d RX DMA\n", case_index);
      printf("LOOPBACK FAIL\n");
      return 1;
    }

    status = eth_status();
    printf("[simloop] case=%d rx_len=%d status=0x%lx\n",
           case_index, rx_len, (unsigned long)status);
    if ((status & error_bits) != 0) {
      printf("[simloop] FAIL case=%d MAC RX error status=0x%lx\n",
             case_index, (unsigned long)status);
      printf("LOOPBACK FAIL\n");
      return 1;
    }
    if (verify_frame(case_index, tx_len, rx_len) < 0) {
      printf("LOOPBACK FAIL\n");
      return 1;
    }
  }

  printf("LOOPBACK PASS\n");
  return 0;
}
