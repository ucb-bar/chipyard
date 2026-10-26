#include "software/ethernet/eth_udp.h"
#include "software/ethernet/ethernet_udp_file_echo.h"

#include <stdio.h>

int main(void) {
  const eth_udp_config_t config = {
      .local_mac = UINT64_C(0x020000000001),
      .local_ip = UINT32_C(0xc0a80180),
      .gateway_ip = UINT32_C(0xc0a80101),
      .subnet_mask = UINT32_C(0xffffff00),
  };
  setvbuf(stdout, NULL, _IONBF, 0);

  if (eth_udp_init(&config) < 0 ||
      ethernet_udp_file_echo_once(false) < 0) {
    printf("[ethernet-udp] FILE TRANSFER FAIL\n");
    return 1;
  }
  printf("[ethernet-udp] FILE TRANSFER PASS\n");
  return 0;
}
