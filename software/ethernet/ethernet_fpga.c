// Bare-metal FPGA-side raw-Ethernet file echo application.

#include "software/ethernet/eth.h"
#include "software/ethernet/ethernet_file_echo.h"
#include "software/ethernet/rtl8211e.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

enum {
  kLinkPollMax = 1000000,
};

// Wait briefly for the PHY link and report the MAC status.
static void wait_for_link(int phy) {
  printf("[ethernet] waiting for link...\n");
  const bool link = phy >= 0 && rtl8211e_wait_for_link(phy, kLinkPollMax);

  if (link) {
    printf("[ethernet] LINK UP speed=%lu status=0x%lx\n", (unsigned long)eth_link_speed(),
           (unsigned long)eth_status());
  } else {
    printf("[ethernet] LINK DOWN timeout; continuing status=0x%lx\n",
           (unsigned long)eth_status());
  }
}

// Run the FPGA-side receive/store/echo service forever.
int main(void) {
  setvbuf(stdout, NULL, _IONBF, 0);
  printf("[ethernet] init\n");

  int phy = rtl8211e_bringup(printf);
  eth_init();
  wait_for_link(phy);

  while (true) {
    if (ethernet_file_echo_once(true) < 0) {
      printf("[ethernet] transfer failed; waiting for next transfer\n");
    }
  }

  return 0;
}
