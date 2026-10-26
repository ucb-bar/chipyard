// Bare-metal DMA driver for the RIVET Ethernet peripheral.

#ifndef SOFTWARE_ETHERNET_ETH_H_
#define SOFTWARE_ETHERNET_ETH_H_

#include "software/ethernet/mmio_config.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Write a 32-bit word to MMIO address `a`.
 *
 * @param a MMIO address.
 * @param v Value to write.
 */
static inline void eth_w32(uintptr_t a, uint32_t v) { *(volatile uint32_t *)a = v; }

/**
 * Read a 32-bit word from MMIO address `a`.
 *
 * @param a MMIO address.
 * @return The value read.
 */
static inline uint32_t eth_r32(uintptr_t a) { return *(volatile uint32_t *)a; }

static inline void eth_w64(uintptr_t a, uint64_t v) { *(volatile uint64_t *)a = v; }
static inline uint64_t eth_r64(uintptr_t a) { return *(volatile uint64_t *)a; }

static inline void eth_fence_rw(void) {
  __asm__ volatile("fence rw, rw" ::: "memory");
}

/**
 * The Ethernet DMA is not coherent with the Rocket D-cache.
 * Ethernet-capable Rocket configurations enable the existing CFLUSH_D_L1 instruction.
 * With rs1=x0 and rs2=x0, 0xfc000073 writes back and invalidates the complete L1 D-cache.
 * FENCE.I cannot be used here: tracked executable DDR disables Rocket's optional D-cache-on-FENCE.I behavior in these configurations.
 * TODO: Find better way to do this.
 */
static inline void eth_dma_cache_sync(void) {
  __asm__ volatile("fence rw, rw\n\t.word 0xfc000073\n\tfence rw, rw" ::: "memory");
}

#define ETH_DMA_BASE ETHERNET_DMA_BASE
#define ETH_DMA_ENABLE (ETH_DMA_BASE + 0x00)
#define ETH_DMA_IDLE (ETH_DMA_BASE + 0x08)
#define ETH_DMA_WATCHDOG (ETH_DMA_BASE + 0x10)
#define ETH_DMA_INT (ETH_DMA_BASE + 0x18)
#define ETH_DMA_S2M_BASE (ETH_DMA_BASE + 0x20)
#define ETH_DMA_S2M_LENGTH (ETH_DMA_BASE + 0x28)
#define ETH_DMA_S2M_CYCLES (ETH_DMA_BASE + 0x30)
#define ETH_DMA_S2M_FIXED (ETH_DMA_BASE + 0x38)
#define ETH_DMA_S2M_TRIGGER (ETH_DMA_BASE + 0x40)
#define ETH_DMA_M2S_BASE (ETH_DMA_BASE + 0x48)
#define ETH_DMA_M2S_LENGTH (ETH_DMA_BASE + 0x50)
#define ETH_DMA_M2S_CYCLES (ETH_DMA_BASE + 0x58)
#define ETH_DMA_M2S_FIXED (ETH_DMA_BASE + 0x60)
#define ETH_DMA_M2S_TRIGGER (ETH_DMA_BASE + 0x68)
#define ETH_DMA_S2M_RESULT (ETH_DMA_BASE + 0x90)

#define ETH_DMA_S2M_RESULT_BEATS_MASK 0x1ffu
#define ETH_DMA_S2M_RESULT_LAST (1u << 9)

#define ETH_DMA_INT_READ_DONE (1u << 0)
#define ETH_DMA_INT_READ_WATCHDOG (1u << 1)
#define ETH_DMA_INT_READ_ERROR (1u << 2)
#define ETH_DMA_INT_WRITE_DONE (1u << 3)
#define ETH_DMA_INT_WRITE_WATCHDOG (1u << 4)
#define ETH_DMA_INT_WRITE_ERROR (1u << 5)
#define ETH_DMA_INT_ERROR_MASK                                                \
  (ETH_DMA_INT_READ_WATCHDOG | ETH_DMA_INT_READ_ERROR |                     \
   ETH_DMA_INT_WRITE_WATCHDOG | ETH_DMA_INT_WRITE_ERROR)

#define ETH_FRONTEND_BASE ETHERNET_FRONTEND_BASE
#define ETH_RX_LEN (ETH_FRONTEND_BASE + 0x00)
#define ETH_RX_COUNT (ETH_FRONTEND_BASE + 0x08)
#define ETH_TX_LEN (ETH_FRONTEND_BASE + 0x10)
#define ETH_TX_SPACE (ETH_FRONTEND_BASE + 0x18)
#define ETH_FRONTEND_INFO (ETH_FRONTEND_BASE + 0x20)
#define ETH_RX_BEATS (ETH_FRONTEND_BASE + 0x28)

#define ETH_RX_LEN_VALUE_MASK 0xffffu
#define ETH_RX_LEN_VALID (1u << 16)
#define ETH_LEN_TO_BEATS(len) (((uint64_t)(len) + 7u) / 8u)

#define ETH_CSR_BASE ETHERNET_CSR_BASE
#define ETH_STATUS (ETH_CSR_BASE + 0x0)
#define ETH_CONTROL (ETH_CSR_BASE + 0x4)

#define ETH_CTRL_IFG(x) ((uint32_t)((x) & 0xffu))
#define ETH_CTRL_TX_EN (1u << 8)
#define ETH_CTRL_RX_EN (1u << 9)

#define ETH_ST_TX_ERR_UNDERFLOW (1u << 0)
#define ETH_ST_TX_FIFO_OVERFLOW (1u << 1)
#define ETH_ST_TX_FIFO_BAD_FR (1u << 2)
#define ETH_ST_TX_FIFO_GOOD_FR (1u << 3)
#define ETH_ST_RX_ERR_BAD_FRAME (1u << 4)
#define ETH_ST_RX_ERR_BAD_FCS (1u << 5)
#define ETH_ST_RX_FIFO_OVERFLOW (1u << 6)
#define ETH_ST_RX_FIFO_BAD_FR (1u << 7)
#define ETH_ST_RX_FIFO_GOOD_FR (1u << 8)
#define ETH_ST_SPEED_SHIFT 9
#define ETH_ST_SPEED_MASK (0x3u << ETH_ST_SPEED_SHIFT)

#ifndef ETH_DMA_POLL_SPINS
#define ETH_DMA_POLL_SPINS 5000000L
#endif

#ifndef ETH_RX_WAIT_SPINS
#define ETH_RX_WAIT_SPINS 50000000L
#endif

/** Wait indefinitely for a queued DMA receive descriptor to complete. */
#define ETH_DMA_WAIT_FOREVER (-1L)
#define ETH_DMA_MAX_CHUNK_BEATS 256u

/**
 * INT is shared by M2S and S2M.
 * All accesses go through this shadow so a DONE observed for the other direction cannot be lost before it is consumed.
 */
static uint64_t eth_dma_int_seen;

static inline uint64_t eth_dma_harvest_int(void) {
  eth_dma_int_seen |= eth_r64(ETH_DMA_INT);
  return eth_dma_int_seen;
}

/**
 * A CSR write wins over a same-cycle completion pulse in the DMA.
 * Clearing is therefore permitted only while neither direction has a descriptor active.
 */
static inline int eth_dma_clear_int_quiescent(void) {
  uint64_t s2m;
  uint64_t m2s;

  eth_dma_harvest_int();
  s2m = eth_r64(ETH_DMA_S2M_TRIGGER);
  m2s = eth_r64(ETH_DMA_M2S_TRIGGER);
  if (s2m != 0 || m2s != 0) {
    return -1;
  }
  eth_dma_harvest_int();
  eth_w64(ETH_DMA_INT, 0);
  eth_dma_int_seen = 0;
  return 0;
}

static inline int eth_dma_poll_direction(uint64_t done_bit, uintptr_t trigger, const char *direction) {
  uint64_t status = 0;
  uint64_t remaining = 0;

  for (long spin = 0; spin < ETH_DMA_POLL_SPINS; spin++) {
    status = eth_dma_harvest_int();
    remaining = eth_r64(trigger);
    if ((status & ETH_DMA_INT_ERROR_MASK) != 0) {
      printf("[eth] %s DMA error: int=0x%lx trigger=%lu\n", direction,
             (unsigned long)status, (unsigned long)remaining);
      return -1;
    }
    if ((status & done_bit) != 0 && remaining == 0) {
      return 0;
    }
  }

  status = eth_dma_harvest_int();
  remaining = eth_r64(trigger);
  printf("[eth] %s DMA timeout: int=0x%lx trigger=%lu s2m=%lu m2s=%lu\n",
         direction, (unsigned long)status, (unsigned long)remaining,
         (unsigned long)eth_r64(ETH_DMA_S2M_TRIGGER),
         (unsigned long)eth_r64(ETH_DMA_M2S_TRIGGER));
  return -1;
}

/**
 * Enable Ethernet TX and RX with the default inter-frame gap.
 */
static inline void eth_init(void) {
  eth_dma_int_seen = 0;
  eth_w64(ETH_DMA_ENABLE, 1);
  eth_w64(ETH_DMA_WATCHDOG, 1000000);
  eth_dma_harvest_int();
  (void)eth_dma_clear_int_quiescent();
  eth_w32(ETH_CONTROL, ETH_CTRL_IFG(12) | ETH_CTRL_TX_EN | ETH_CTRL_RX_EN);
}

/**
 * Read the MAC status register.
 *
 * @return The raw status word (`ETH_ST_*` fields).
 */
static inline uint32_t eth_status(void) { return eth_r32(ETH_STATUS); }

/**
 * Report the negotiated link speed.
 *
 * @return 0 = 10M, 1 = 100M, 2 = 1G.
 */
static inline uint32_t eth_link_speed(void) {
  return (eth_status() & ETH_ST_SPEED_MASK) >> ETH_ST_SPEED_SHIFT;
}

/** Copy a payload into the shared TX bounce buffer and acquire the DMA. */
static inline int eth_dma_prepare_tx_bytes(
    const uint8_t *buf, int len, int maximum_length) {
  volatile uint8_t *bounce = (volatile uint8_t *)(uintptr_t)ETH_DMA_TX_BUF;

  if (buf == NULL || len < 1 || len > maximum_length) {
    printf("[eth] invalid TX length %d (max %d)\n", len, maximum_length);
    return -1;
  }
  for (int i = 0; i < len; i++) {
    bounce[i] = buf[i];
  }
  eth_dma_cache_sync();
  if (eth_dma_clear_int_quiescent() < 0) {
    printf("[eth] TX start while DMA active: s2m=%lu m2s=%lu\n",
           (unsigned long)eth_r64(ETH_DMA_S2M_TRIGGER),
           (unsigned long)eth_r64(ETH_DMA_M2S_TRIGGER));
    return -1;
  }
  return 0;
}

/** Transmit the prepared TX bounce-buffer bytes through the M2S DMA. */
static inline int eth_dma_send_prepared_bytes(int len) {
  uint64_t beats = ETH_LEN_TO_BEATS(len);
  uint64_t done_beats = 0;

  while (done_beats < beats) {
    uint64_t chunk = beats - done_beats;
    if (chunk > ETH_DMA_MAX_CHUNK_BEATS) {
      chunk = ETH_DMA_MAX_CHUNK_BEATS;
    }

    eth_w64(ETH_DMA_M2S_BASE, ETH_DMA_TX_BUF + done_beats * 8);
    eth_w64(ETH_DMA_M2S_LENGTH, chunk - 1);
    eth_w64(ETH_DMA_M2S_CYCLES, 0);
    eth_w64(ETH_DMA_M2S_FIXED, 0);
    eth_w64(ETH_DMA_M2S_TRIGGER, 1);
    if (eth_dma_poll_direction(
            ETH_DMA_INT_READ_DONE, ETH_DMA_M2S_TRIGGER, "TX") < 0) {
      return -1;
    }
    done_beats += chunk;

    if (done_beats < beats && eth_dma_clear_int_quiescent() < 0) {
      printf("[eth] TX chunk ended with DMA still active\n");
      return -1;
    }
  }
  return 0;
}

/**
 * Transmit one L2 frame. Do not include FCS.
 *
 * @param buf Frame bytes.
 * @param len Frame length in bytes. Must be at least 1.
 */
static inline int eth_send_frame_bounded(const uint8_t *buf, int len) {
  if (eth_dma_prepare_tx_bytes(buf, len, ETH_MAX_FRAME) < 0) {
    return -1;
  }

  for (long spin = 0; eth_r64(ETH_TX_SPACE) == 0; spin++) {
    if (spin >= ETH_DMA_POLL_SPINS) {
      printf("[eth] TX length queue full\n");
      return -1;
    }
  }

  /* Length must be visible before the first DMA beat can reach the frontend. */
  eth_w64(ETH_TX_LEN, (uint64_t)len);
  return eth_dma_send_prepared_bytes(len);
}

static inline void eth_send_frame(const uint8_t *buf, int len) {
  (void)eth_send_frame_bounded(buf, len);
}

/**
 * Receive one stream frame and its matching frontend descriptor into `buf`.
 *
 * If the frame is longer than `maxlen`, the excess bytes are drained and the
 * full received length is still returned. A finite timeout does not cancel the
 * queued S2M DMA descriptor. A caller that intends to receive another frame
 * after an idle period must use #ETH_DMA_WAIT_FOREVER unless it first resets or
 * otherwise cancels the DMA request.
 *
 * @param buf Destination buffer.
 * @param maxlen Capacity of `buf`.
 * @param wait_spins Maximum polling iterations, or #ETH_DMA_WAIT_FOREVER.
 * @param descriptor_count_address Frontend descriptor-count CSR address.
 * @param descriptor_info_address Frontend descriptor-information CSR address.
 * @param descriptor_valid_mask Valid bit in the descriptor-information CSR.
 * @param descriptor_pop_address Optional descriptor-pop CSR address, or zero.
 * @return Received frame length in bytes.
 */
static inline int eth_dma_receive_with_descriptor(
    uint8_t *buf, int maxlen, long wait_spins,
    uintptr_t descriptor_count_address, uintptr_t descriptor_info_address,
    uint64_t descriptor_valid_mask, uintptr_t descriptor_pop_address) {
  uint64_t len_word;
  uint64_t length;
  uint64_t beats;
  uint64_t done_beats = 0;
  const uint64_t max_beats = ETH_LEN_TO_BEATS(ETH_MAX_FRAME);
  bool ended_frame = false;

  if (maxlen < 0 || (maxlen > 0 && buf == NULL)) {
    return -1;
  }

  /**
   * The S2M descriptor must be active before the frame finishes:
   * RX_LEN is produced by the same final stream beat that now shortens the descriptor.
   * Discard cached data from the previous transfer before DMA ownership.
   */
  eth_dma_cache_sync();

  if (eth_dma_clear_int_quiescent() < 0) {
    printf("[eth] RX start while DMA active: s2m=%lu m2s=%lu\n",
           (unsigned long)eth_r64(ETH_DMA_S2M_TRIGGER),
           (unsigned long)eth_r64(ETH_DMA_M2S_TRIGGER));
    return -1;
  }

  while (done_beats < max_beats && !ended_frame) {
    uint64_t chunk = max_beats - done_beats;
    uint64_t result;
    uint64_t actual_beats;
    uint64_t status = 0;
    uint64_t remaining = 0;
    long spin = 0;

    if (chunk > ETH_DMA_MAX_CHUNK_BEATS) {
      chunk = ETH_DMA_MAX_CHUNK_BEATS;
    }

    eth_w64(ETH_DMA_S2M_BASE, ETH_DMA_RX_BUF + done_beats * 8);
    eth_w64(ETH_DMA_S2M_LENGTH, chunk - 1);
    eth_w64(ETH_DMA_S2M_CYCLES, 0);
    eth_w64(ETH_DMA_S2M_FIXED, 0);
    eth_w64(ETH_DMA_S2M_TRIGGER, 1);
#ifdef ETH_DEBUG
    printf("[eth] RX programmed: base=0x%lx length=%lu trigger=%lu\n",
           (unsigned long)eth_r64(ETH_DMA_S2M_BASE),
           (unsigned long)eth_r64(ETH_DMA_S2M_LENGTH),
           (unsigned long)eth_r64(ETH_DMA_S2M_TRIGGER));
#endif
    for (;;) {
      status = eth_dma_harvest_int();
      remaining = eth_r64(ETH_DMA_S2M_TRIGGER);
      if ((status & ETH_DMA_INT_ERROR_MASK) != 0) {
        printf("[eth] RX DMA error: int=0x%lx trigger=%lu\n",
               (unsigned long)status, (unsigned long)remaining);
        return -1;
      }
      if ((status & ETH_DMA_INT_WRITE_DONE) != 0 && remaining == 0) {
        break;
      }
      if (wait_spins >= 0 && spin++ >= wait_spins) {
        printf("[eth] RX frame timeout: int=0x%lx s2m=%lu m2s=%lu count=%lu\n",
               (unsigned long)status,
               (unsigned long)remaining,
               (unsigned long)eth_r64(ETH_DMA_M2S_TRIGGER),
               (unsigned long)eth_r64(descriptor_count_address));
        return -1;
      }
    }

    result = eth_r64(ETH_DMA_S2M_RESULT);
    actual_beats = result & ETH_DMA_S2M_RESULT_BEATS_MASK;
    ended_frame = (result & ETH_DMA_S2M_RESULT_LAST) != 0;
    if (actual_beats == 0 || actual_beats > chunk ||
        (!ended_frame && actual_beats != chunk)) {
      printf("[eth] invalid RX DMA result: result=0x%lx chunk=%lu\n",
             (unsigned long)result, (unsigned long)chunk);
      return -1;
    }
#ifdef ETH_DEBUG
    printf("[eth] RX complete: int=0x%lx trigger=%lu actual=%lu last=%u rx_count=%lu rx_beats=%lu\n",
           (unsigned long)eth_dma_harvest_int(),
           (unsigned long)eth_r64(ETH_DMA_S2M_TRIGGER),
           (unsigned long)actual_beats, ended_frame ? 1u : 0u,
           (unsigned long)eth_r64(descriptor_count_address),
           (unsigned long)eth_r64(ETH_RX_BEATS));
#endif
    done_beats += actual_beats;

    if (!ended_frame && eth_dma_clear_int_quiescent() < 0) {
      printf("[eth] RX chunk ended with DMA still active\n");
      return -1;
    }
  }

  if (!ended_frame) {
    printf("[eth] RX frame exceeds maximum %d bytes\n", ETH_MAX_FRAME);
    return -1;
  }

  for (long spin = 0; eth_r64(descriptor_count_address) == 0; spin++) {
    if (spin >= ETH_DMA_POLL_SPINS) {
      printf("[eth] RX DMA saw last but RX_LEN was not published\n");
      return -1;
    }
  }
  len_word = eth_r64(descriptor_info_address);
  if ((len_word & descriptor_valid_mask) == 0) {
    printf("[eth] RX_COUNT nonzero but RX_LEN invalid\n");
    return -1;
  }
  length = len_word & ETH_RX_LEN_VALUE_MASK;
  beats = ETH_LEN_TO_BEATS(length);
  if (length == 0 || beats != done_beats) {
    printf("[eth] RX length/result mismatch: length=%lu beats=%lu DMA=%lu\n",
           (unsigned long)length, (unsigned long)beats,
           (unsigned long)done_beats);
    return -1;
  }
  /* Discard any RX-buffer lines cached by this or a previous transfer before
   * the CPU consumes the frame written by DMA. */
  eth_dma_cache_sync();

  {
    volatile const uint8_t *bounce =
        (volatile const uint8_t *)(uintptr_t)ETH_DMA_RX_BUF;
    int copied = (length < (uint64_t)maxlen) ? (int)length : maxlen;
    for (int i = 0; i < copied; i++) {
      buf[i] = bounce[i];
    }
  }
  if (descriptor_pop_address != 0) {
    eth_w64(descriptor_pop_address, 1);
  }
  return (int)length;
}

static inline int eth_recv_frame_with_limit(uint8_t *buf, int maxlen,
                                            long wait_spins) {
  return eth_dma_receive_with_descriptor(
      buf, maxlen, wait_spins, ETH_RX_COUNT, ETH_RX_LEN,
      ETH_RX_LEN_VALID, 0);
}

static inline int eth_recv_frame_bounded(uint8_t *buf, int maxlen, int *rx_len) {
  int result = eth_recv_frame_with_limit(buf, maxlen, ETH_RX_WAIT_SPINS);
  if (result < 0) {
    return -1;
  }
  if (rx_len != NULL) {
    *rx_len = result;
  }
  return 0;
}

static inline int eth_recv_frame(uint8_t *buf, int maxlen) {
  return eth_recv_frame_with_limit(buf, maxlen, ETH_DMA_WAIT_FOREVER);
}

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_ETH_H_
