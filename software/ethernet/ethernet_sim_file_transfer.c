// One-shot bare-metal file-transfer application for Verilator Ethernet peers.

#include "software/ethernet/eth.h"
#include "software/ethernet/ethernet_file_echo.h"

#include <stdio.h>

int main(void) {
  setvbuf(stdout, NULL, _IONBF, 0);

  eth_init();
  if (ethernet_file_echo_once(false) < 0) {
    printf("[ethernet] FILE TRANSFER FAIL\n");
    return 1;
  }

  printf("[ethernet] FILE TRANSFER PASS\n");
  return 0;
}
