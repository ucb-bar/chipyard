#ifndef SOFTWARE_ETHERNET_ETHERNET_FILE_TRANSPORT_H_
#define SOFTWARE_ETHERNET_ETHERNET_FILE_TRANSPORT_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Packet transport used by the shared `EFT1` receive/store/echo engine. */
typedef struct {
  void *context;
  int (*send)(void *context, const uint8_t *packet, size_t length);
  int (*receive)(void *context, uint8_t *packet, size_t capacity,
                 size_t *length);
} ethernet_file_transport_t;

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // SOFTWARE_ETHERNET_ETHERNET_FILE_TRANSPORT_H_
