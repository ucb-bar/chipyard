// On-board RGMII loopback self-test using the RTL8211E internal loopback.

#include "software/ethernet/eth.h"
#include "software/ethernet/rtl8211e.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

#ifndef PHY_LOOPBACK_SPEED_MBPS
#define PHY_LOOPBACK_SPEED_MBPS 100
#endif

#if PHY_LOOPBACK_SPEED_MBPS == 1000
#define PHY_LOOPBACK_BMCR_SPEED BMCR_SPEED1000
#define PHY_LOOPBACK_MAC_SPEED 2u
#define PHY_LOOPBACK_SPEED_NAME "1G"
#elif PHY_LOOPBACK_SPEED_MBPS == 100
#define PHY_LOOPBACK_BMCR_SPEED BMCR_SPEED100
#define PHY_LOOPBACK_MAC_SPEED 1u
#define PHY_LOOPBACK_SPEED_NAME "100M"
#else
#error "PHY_LOOPBACK_SPEED_MBPS must be 100 or 1000"
#endif

enum {
  kEthertype = 0x88B5,
  kMdioWaitSpins = 2000000,
  kPhyResetSpins = 2000000,
  kPhySettle = 50000000,
  kMacSpeedWaitSpins = 5000000,
#ifdef ETH_DEBUG
  kDumpBytes = 64,
#else
  kDumpBytes = 32,
#endif
};

static const int kFrameLengths[] = {60, 61, 67, 1514, 2049, 3000, 4095, 4096};

// Keep the first two DMA beats distinctive so zero fill, lane reversal, and
// byte shifts are immediately visible in the board diagnostic.
static const uint8_t kDstMac[6] = {0x02, 0x11, 0x22, 0x33, 0x44, 0x55};
static const uint8_t kSrcMac[6] = {0x06, 0x67, 0x78, 0x89, 0x9a, 0xab};

static uint8_t tx_frame[ETH_MAX_FRAME];
static uint8_t rx_frame[ETH_MAX_FRAME];

// Dump a bounded prefix without allowing the compiler to cache DMA-buffer reads.
static void dump_bytes(const char *name, volatile const uint8_t *data, int length) {
  int count = length < kDumpBytes ? length : kDumpBytes;
  printf("[phyloop] %s (%d bytes):", name, count);
  for (int i = 0; i < count; i++) {
    if ((i & 7) == 0) {
      printf("\n[phyloop]   %02d:", i);
    }
    printf(" %02x", (unsigned)data[i]);
  }
  printf("\n");
}

// Print a compact snapshot of MAC and MDIO state.
static void print_status_snapshot(const char *where) {
  printf("[phyloop] %s: mdio_stat=0x%lx tx_space=%lu rx_count=%lu "
         "dma_int=0x%lx s2m=%lu m2s=%lu eth_status=0x%lx\n",
         where, (unsigned long)mdio_r32(MDIO_STAT),
         (unsigned long)eth_r64(ETH_TX_SPACE),
         (unsigned long)eth_r64(ETH_RX_COUNT),
         (unsigned long)eth_r64(ETH_DMA_INT),
         (unsigned long)eth_r64(ETH_DMA_S2M_TRIGGER),
         (unsigned long)eth_r64(ETH_DMA_M2S_TRIGGER),
         (unsigned long)eth_status());
}

// Wait for the MDIO master to become idle with a bounded timeout.
static int mdio_wait_idle_bounded(const char *where) {
  for (long spin = 0; spin < kMdioWaitSpins; spin++) {
    uint32_t status = mdio_r32(MDIO_STAT);
    if ((status & MDIO_STAT_BUSY) == 0) {
      return 0;
    }
  }
  printf("[phyloop] timeout: MDIO busy during %s\n", where);
  print_status_snapshot("mdio timeout");
  return -1;
}

// Read one PHY register with bounded MDIO waits.
static int mdio_read_bounded(int phy, int reg, uint16_t *value, const char *where) {
  if (mdio_wait_idle_bounded(where) < 0) {
    return -1;
  }
  mdio_w32(MDIO_CMD, mdio_pack_cmd(phy, reg, MDIO_OP_READ));
  if (mdio_wait_idle_bounded(where) < 0) {
    return -1;
  }
  *value = (uint16_t)(mdio_r32(MDIO_RDATA) & 0xffffu);
  return 0;
}

// Write one PHY register with bounded MDIO waits.
static int mdio_write_bounded(int phy, int reg, uint16_t value, const char *where) {
  if (mdio_wait_idle_bounded(where) < 0) {
    return -1;
  }
  mdio_w32(MDIO_WDATA, value);
  mdio_w32(MDIO_CMD, mdio_pack_cmd(phy, reg, MDIO_OP_WRITE));
  return mdio_wait_idle_bounded(where);
}

// Read, modify, and write one PHY register with bounded MDIO waits.
static int mdio_rmw_bounded(int phy, int reg, uint16_t clear, uint16_t set,
                            const char *where) {
  uint16_t value = 0;
  if (mdio_read_bounded(phy, reg, &value, where) < 0) {
    return -1;
  }
  value = (uint16_t)((value & ~clear) | set);
  return mdio_write_bounded(phy, reg, value, where);
}

// Reset the PHY and wait for the self-clearing BMCR reset bit.
// The RTL8211E requires a reset/restart/power transition before speed and duplex writes take effect.
static int phy_reset_bounded(int phy) {
  if (mdio_write_bounded(phy, MII_BMCR, BMCR_RESET, "reset PHY") < 0) {
    return -1;
  }
  for (long spin = 0; spin < kPhyResetSpins; spin++) {
    uint16_t bmcr = 0;
    if (mdio_read_bounded(phy, MII_BMCR, &bmcr, "wait PHY reset") < 0) {
      return -1;
    }
    if ((bmcr & BMCR_RESET) == 0) {
      return 0;
    }
  }
  printf("[phyloop] timeout: PHY reset did not complete\n");
  return -1;
}

// Scan the MDIO bus for an RTL8211E PHY.
static int rtl8211e_find_phy_bounded(uint16_t *id1_out, uint16_t *id2_out) {
  for (int phy = 0; phy < 32; phy++) {
    uint16_t id1 = 0;
    uint16_t id2 = 0;
    if (mdio_read_bounded(phy, MII_PHYID1, &id1, "find PHYID1") < 0 ||
        mdio_read_bounded(phy, MII_PHYID2, &id2, "find PHYID2") < 0) {
      return -2;
    }
    if (id1 == RTL8211E_ID1 && (id2 & RTL8211E_ID2_MASK) == RTL8211E_ID2) {
      *id1_out = id1;
      *id2_out = id2;
      return phy;
    }
  }
  return -1;
}

// Configure the RTL8211E RGMII clock delay bits.
static int rtl8211e_set_rgmii_delay_bounded(int phy, int rx, int tx) {
  uint16_t set = (uint16_t)((rx ? RTL8211E_RX_DELAY : 0) | (tx ? RTL8211E_TX_DELAY : 0));
  if (mdio_write_bounded(phy, RTL_PAGE_SELECT, 0x0007, "select ext page mode") < 0 ||
      mdio_write_bounded(phy, RTL_EXT_PAGE_SELECT, RTL8211E_EXT_PAGE,
                         "select RGMII page") < 0 ||
      mdio_rmw_bounded(phy, RTL8211E_RGMII_CFG_REG,
                       RTL8211E_RX_DELAY | RTL8211E_TX_DELAY, set,
                       "set RGMII delay") < 0 ||
      mdio_write_bounded(phy, RTL_PAGE_SELECT, 0x0000, "restore page 0") < 0) {
    return -1;
  }
  return 0;
}

// Put the PHY into forced PCS loopback at the compile-time selected speed.
static int phy_enable_loopback_bounded(int phy) {
  if (mdio_rmw_bounded(phy, MII_CTRL1000,
                       CTRL1000_MANUAL | CTRL1000_MASTER, CTRL1000_FULL,
                       "clear gigabit master override") < 0 ||
      mdio_write_bounded(phy, MII_BMCR,
                         BMCR_LOOPBACK | PHY_LOOPBACK_BMCR_SPEED | BMCR_FULLDPLX |
                             BMCR_ANRESTART,
                         "enable PHY loopback") < 0) {
    return -1;
  }
  return 0;
}

// Leave PHY loopback and restart auto-negotiation.
static void phy_disable_loopback_bounded(int phy) {
  (void)mdio_rmw_bounded(phy, MII_CTRL1000, CTRL1000_MANUAL | CTRL1000_MASTER,
                         CTRL1000_FULL, "clear master/slave override");
  (void)mdio_write_bounded(phy, MII_BMCR, BMCR_ANENABLE | BMCR_ANRESTART,
                           "disable PHY loopback");
}

// Find the PHY and configure it for the loopback test.
static int setup_phy_loopback(int *phy_out) {
  uint16_t id1 = 0;
  uint16_t id2 = 0;
  int phy = rtl8211e_find_phy_bounded(&id1, &id2);
  if (phy == -2) {
    return -1;
  }
  if (phy < 0) {
    printf("[phyloop] no RTL8211E on MDIO bus\n");
    return -1;
  }
  printf("[phyloop] RTL8211E at phy=%d id=%04x%04x\n", phy, id1, id2);

  // Reset first because it may restore extended-page delay registers.
  if (phy_reset_bounded(phy) < 0 ||
      rtl8211e_set_rgmii_delay_bounded(phy, /*rx=*/1, /*tx=*/0) < 0 ||
      phy_enable_loopback_bounded(phy) < 0) {
    return -1;
  }
  mdio_delay(kPhySettle);

  uint16_t bmcr = 0;
  uint16_t bmsr = 0;
  uint16_t physr = 0;
  if (mdio_read_bounded(phy, MII_BMCR, &bmcr, "read BMCR") < 0 ||
      mdio_read_bounded(phy, MII_BMSR, &bmsr, "read BMSR") < 0 ||
      mdio_read_bounded(phy, MII_BMSR, &bmsr, "read BMSR latch") < 0 ||
      mdio_read_bounded(phy, MII_PHYSR, &physr, "read PHYSR") < 0) {
    phy_disable_loopback_bounded(phy);
    return -1;
  }
  printf("[phyloop] loopback enabled, bmcr=%04x bmsr=%04x physr=%04x link=%u\n",
         bmcr, bmsr, physr, (bmsr & BMSR_LSTATUS) ? 1u : 0u);
  const uint16_t mode_mask =
      BMCR_LOOPBACK | BMCR_SPEED100 | BMCR_SPEED1000 | BMCR_FULLDPLX;
  const uint16_t expected_mode =
      BMCR_LOOPBACK | PHY_LOOPBACK_BMCR_SPEED | BMCR_FULLDPLX;
  if ((bmcr & mode_mask) != expected_mode) {
    printf("[phyloop] invalid loopback BMCR=0x%04x\n", bmcr);
    phy_disable_loopback_bounded(phy);
    return -1;
  }
  *phy_out = phy;
  return 0;
}

// Wait for the MAC's RGMII clock detector to observe the selected speed.
static int wait_for_mac_speed(void) {
  for (long spin = 0; spin < kMacSpeedWaitSpins; spin++) {
    if (eth_link_speed() == PHY_LOOPBACK_MAC_SPEED) {
      return 0;
    }
  }
  printf("[phyloop] timeout: MAC did not detect %s, status=0x%lx\n",
         PHY_LOOPBACK_SPEED_NAME, (unsigned long)eth_status());
  return -1;
}

// Build a deterministic Ethernet frame for one test case.
static int build_test_frame(uint8_t frame[ETH_MAX_FRAME], int length, int case_index) {
  int n = 0;
  for (int i = 0; i < 6; i++) {
    frame[n++] = kDstMac[i];
  }
  for (int i = 0; i < 6; i++) {
    frame[n++] = kSrcMac[i];
  }
  frame[n++] = (uint8_t)(kEthertype >> 8);
  frame[n++] = (uint8_t)(kEthertype & 0xffu);
  while (n < length) {
    frame[n] = (uint8_t)(0x5a + n * 29 + case_index * 17);
    n++;
  }
  return n;
}

// Send the deterministic loopback test frame.
static int send_test_frame(int length, int case_index) {
  int tx_len = build_test_frame(tx_frame, length, case_index);
  printf("[phyloop] case=%d tx len=%d\n", case_index, tx_len);
  if (tx_len != length) {
    return -1;
  }
  dump_bytes("TX application", tx_frame, tx_len);
  if (eth_send_frame_bounded(tx_frame, tx_len) < 0) {
    return -1;
  }
  dump_bytes("TX DMA buffer",
             (volatile const uint8_t *)(uintptr_t)ETH_DMA_TX_BUF, tx_len);
  return 0;
}

// Receive the looped-back test frame and capture MAC status.
static int receive_test_frame(int *rx_len, uint32_t *status) {
  if (eth_recv_frame_bounded(rx_frame, (int)sizeof(rx_frame), rx_len) < 0) {
    return -1;
  }
  *status = eth_status();
  printf("[phyloop] rx len=%d status=0x%lx\n", *rx_len, (unsigned long)*status);
  dump_bytes("RX DMA buffer",
             (volatile const uint8_t *)(uintptr_t)ETH_DMA_RX_BUF, *rx_len);
  dump_bytes("RX application", rx_frame, *rx_len);
  return 0;
}

// Verify the complete received frame and report RX status warnings.
static int verify_frame(int rx_len, int tx_len, uint32_t status) {
  if (rx_len != tx_len) {
    printf("[phyloop] FAIL rx length %d != %d\n", rx_len, tx_len);
    return -1;
  }
  for (int i = 0; i < tx_len; i++) {
    if (rx_frame[i] != tx_frame[i]) {
      printf("[phyloop] FAIL byte[%d]=0x%02x exp 0x%02x\n", i,
             rx_frame[i], tx_frame[i]);
      return -1;
    }
  }

  uint32_t error_bits = ETH_ST_RX_ERR_BAD_FRAME | ETH_ST_RX_ERR_BAD_FCS |
                        ETH_ST_RX_FIFO_OVERFLOW;
  if ((status & error_bits) != 0) {
    printf("[phyloop] WARN rx error/overflow bits: 0x%lx\n", (unsigned long)status);
  }
  return 0;
}

// Run all boundary cases through the real MAC/PHY/DMA loopback path.
static int run_frame_tests(void) {
  int frame_count = (int)(sizeof(kFrameLengths) / sizeof(kFrameLengths[0]));

  for (int case_index = 0; case_index < frame_count; case_index++) {
    int tx_len = kFrameLengths[case_index];
    int rx_len = 0;
    uint32_t status = 0;
    if (send_test_frame(tx_len, case_index) < 0 ||
        receive_test_frame(&rx_len, &status) < 0 ||
        verify_frame(rx_len, tx_len, status) < 0) {
      return -1;
    }
    printf("[phyloop] case=%d PASS len=%d\n", case_index, tx_len);
  }
  return 0;
}

// Run one PHY loopback send/receive/verify cycle.
int main(void) {
  setvbuf(stdout, NULL, _IONBF, 0);
  printf("[phyloop] start\n");

  int phy = -1;
  if (setup_phy_loopback(&phy) < 0) {
    printf("LOOPBACK FAIL\n");
    return 1;
  }

  eth_init();
  printf("[phyloop] frontend=0x%lx tx_buf=0x%lx rx_buf=0x%lx\n",
         (unsigned long)eth_r64(ETH_FRONTEND_INFO),
         (unsigned long)ETH_DMA_TX_BUF, (unsigned long)ETH_DMA_RX_BUF);
  if (eth_r64(ETH_RX_COUNT) != 0) {
    printf("[phyloop] stale RX frames present after init; reset the target\n");
    phy_disable_loopback_bounded(phy);
    printf("LOOPBACK FAIL\n");
    return 1;
  }
  if (wait_for_mac_speed() < 0) {
    phy_disable_loopback_bounded(phy);
    printf("LOOPBACK FAIL\n");
    return 1;
  }
  printf("[phyloop] MAC detected %s\n", PHY_LOOPBACK_SPEED_NAME);
  int ok = run_frame_tests() == 0;

  phy_disable_loopback_bounded(phy);

  if (ok) {
    printf("LOOPBACK PASS\n");
    return 0;
  }
  printf("LOOPBACK FAIL\n");
  return 1;
}
