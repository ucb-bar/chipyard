// Realtek RTL8211E PHY helper over the MDIO bus.

#ifndef SOFTWARE_ETHERNET_RTL8211E_H_
#define SOFTWARE_ETHERNET_RTL8211E_H_

#include "software/ethernet/mdio.h"

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MII_BMCR 0x00
#define MII_BMSR 0x01
#define MII_PHYID1 0x02
#define MII_PHYID2 0x03
#define MII_CTRL1000 0x09
#define MII_PHYSR 0x11

#define BMCR_RESET 0x8000
#define BMCR_LOOPBACK 0x4000
#define BMCR_SPEED100 0x2000
#define BMCR_ANENABLE 0x1000
#define BMCR_FULLDPLX 0x0100
#define BMCR_SPEED1000 0x0040
#define BMCR_ANRESTART 0x0200
#define BMSR_LSTATUS 0x0004
#define BMSR_ANEGCOMP 0x0020

#define CTRL1000_MANUAL 0x1000
#define CTRL1000_MASTER 0x0800
#define CTRL1000_FULL 0x0200

#define RTL8211E_ID1 0x001c
#define RTL8211E_ID2_MASK 0xfff0
#define RTL8211E_ID2 0xc910

#define RTL8211E_DEFAULT_PHYAD 1

#define RTL_PAGE_SELECT 0x1f
#define RTL_EXT_PAGE_SELECT 0x1e
#define RTL8211E_EXT_PAGE 0x00a4
#define RTL8211E_RGMII_CFG_REG 0x1c
#define RTL8211E_TX_DELAY (1u << 1)
#define RTL8211E_RX_DELAY (1u << 2)

#ifndef RTL8211E_RGMII_RX_DELAY_ENABLE
#define RTL8211E_RGMII_RX_DELAY_ENABLE 1
#endif

#ifndef RTL8211E_RGMII_TX_DELAY_ENABLE
#define RTL8211E_RGMII_TX_DELAY_ENABLE 0
#endif

/**
 * Report PHY link status.
 *
 * @param phy PHY address.
 * @return 1 if link is up, 0 otherwise.
 */
static inline int rtl8211e_link_up(int phy) {
  (void)mdio_read(phy, MII_BMSR);
  return (mdio_read(phy, MII_BMSR) & BMSR_LSTATUS) ? 1 : 0;
}

/**
 * Poll until the PHY link is up or the polling limit is reached.
 *
 * @param phy PHY address.
 * @param max_polls Maximum number of link-status polls.
 * @return 1 if link is up, 0 if the polling limit is reached.
 */
static inline int rtl8211e_wait_for_link(int phy, uint32_t max_polls) {
  for (uint32_t polls = 0; polls < max_polls; polls++) {
    if (rtl8211e_link_up(phy)) {
      return 1;
    }
  }
  return 0;
}

/**
 * Find and configure the RTL8211E for cable link mode.
 *
 * @param print Optional printf-compatible status callback.
 * @return PHY address on success, -1 if no RTL8211E is found.
 */
static inline int rtl8211e_bringup(int (*print)(const char *, ...)) {
  int phy = -1;
  uint16_t id1 = 0;
  uint16_t id2 = 0;
  for (int candidate = 0; candidate < 32; candidate++) {
    id1 = mdio_read(candidate, MII_PHYID1);
    id2 = mdio_read(candidate, MII_PHYID2);
    if (id1 == RTL8211E_ID1 && (id2 & RTL8211E_ID2_MASK) == RTL8211E_ID2) {
      phy = candidate;
      break;
    }
  }

  if (phy < 0) {
    if (print != 0) {
      print("[mdio] no RTL8211E found on the MDIO bus\n");
    }
    return -1;
  }

  if (print != 0) {
    print("[mdio] RTL8211E at phy=%d id=%04x%04x\n", phy, id1, id2);
  }

  uint16_t set = (uint16_t)((RTL8211E_RGMII_RX_DELAY_ENABLE ? RTL8211E_RX_DELAY : 0) |
                            (RTL8211E_RGMII_TX_DELAY_ENABLE ? RTL8211E_TX_DELAY : 0));
  mdio_write(phy, RTL_PAGE_SELECT, 0x0007);
  mdio_write(phy, RTL_EXT_PAGE_SELECT, RTL8211E_EXT_PAGE);
  uint16_t delay = mdio_read(phy, RTL8211E_RGMII_CFG_REG);
  delay = (uint16_t)((delay & ~(RTL8211E_RX_DELAY | RTL8211E_TX_DELAY)) | set);
  mdio_write(phy, RTL8211E_RGMII_CFG_REG, delay);
  mdio_write(phy, RTL_PAGE_SELECT, 0x0000);

  uint16_t ctrl1000 = mdio_read(phy, MII_CTRL1000);
  ctrl1000 = (uint16_t)((ctrl1000 & ~(CTRL1000_MANUAL | CTRL1000_MASTER)) |
                        CTRL1000_FULL);
  mdio_write(phy, MII_CTRL1000, ctrl1000);
  mdio_write(phy, MII_BMCR, BMCR_ANENABLE | BMCR_ANRESTART);

  if (print != 0) {
    print("[mdio] rgmii delay rx=%d tx=%d\n", RTL8211E_RGMII_RX_DELAY_ENABLE,
          RTL8211E_RGMII_TX_DELAY_ENABLE);
    print("[mdio] auto-negotiation restarted\n");
  }
  return phy;
}

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_RTL8211E_H_
