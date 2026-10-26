#ifndef SOFTWARE_ETHERNET_ETHERNET_UDP_FILE_ECHO_H_
#define SOFTWARE_ETHERNET_ETHERNET_UDP_FILE_ECHO_H_

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Receive and echo one `EFT1` transfer over UDP port 1234. */
int ethernet_udp_file_echo_once(bool verbose);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_ETHERNET_UDP_FILE_ECHO_H_
