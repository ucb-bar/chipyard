// Bare-metal driver for the RIVET MMIO MDIO master.

#ifndef SOFTWARE_ETHERNET_MDIO_H_
#define SOFTWARE_ETHERNET_MDIO_H_

#include "software/ethernet/mmio_config.h"

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Write a 32-bit word to MMIO address `a`.
 *
 * @param a MMIO address.
 * @param v Value to write.
 */
static inline void mdio_w32(uintptr_t a, uint32_t v) { *(volatile uint32_t *)a = v; }

/**
 * Read a 32-bit word from MMIO address `a`.
 *
 * @param a MMIO address.
 * @return The value read.
 */
static inline uint32_t mdio_r32(uintptr_t a) { return *(volatile uint32_t *)a; }

#define MDIO_BASE ETHERNET_MDIO_BASE
#define MDIO_WDATA (MDIO_BASE + 0x00)
#define MDIO_CMD (MDIO_BASE + 0x04)
#define MDIO_STAT (MDIO_BASE + 0x08)
#define MDIO_RDATA (MDIO_BASE + 0x0c)
#define MDIO_PRESC (MDIO_BASE + 0x10)

#define MDIO_STAT_BUSY (1u << 0)
#define MDIO_STAT_RVALID (1u << 1)

#define MDIO_OP_WRITE 0x1u
#define MDIO_OP_READ 0x2u

/**
 * Pack a `{reg, phy, op}` command word.
 *
 * @param phy PHY address.
 * @param reg Register address.
 * @param op Opcode (`MDIO_OP_READ` or `MDIO_OP_WRITE`).
 * @return Packed command word.
 */
static inline uint32_t mdio_pack_cmd(int phy, int reg, uint32_t op) {
  return ((uint32_t)(reg & 0x1f) << 7) | ((uint32_t)(phy & 0x1f) << 2) | (op & 0x3u);
}

/**
 * Spin until the MDIO master is idle.
 */
static inline void mdio_wait_idle(void) {
  while (mdio_r32(MDIO_STAT) & MDIO_STAT_BUSY) {
    // Spin.
  }
}

/**
 * Write one PHY register over MDIO.
 *
 * @param phy PHY address.
 * @param reg Register address.
 * @param val Value to write.
 */
static inline void mdio_write(int phy, int reg, uint16_t val) {
  mdio_wait_idle();
  mdio_w32(MDIO_WDATA, val);
  mdio_w32(MDIO_CMD, mdio_pack_cmd(phy, reg, MDIO_OP_WRITE));
  mdio_wait_idle();
}

/**
 * Read one PHY register over MDIO.
 *
 * @param phy PHY address.
 * @param reg Register address.
 * @return Register value.
 */
static inline uint16_t mdio_read(int phy, int reg) {
  mdio_wait_idle();
  mdio_w32(MDIO_CMD, mdio_pack_cmd(phy, reg, MDIO_OP_READ));
  mdio_wait_idle();
  return (uint16_t)(mdio_r32(MDIO_RDATA) & 0xffffu);
}

/**
 * Crude busy-wait delay.
 *
 * @param iters Number of spin iterations.
 */
static inline void mdio_delay(long iters) {
  for (volatile long i = 0; i < iters; i++) {
    // Spin.
  }
}

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_MDIO_H_
