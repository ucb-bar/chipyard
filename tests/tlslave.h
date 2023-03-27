#include <stdint.h>

#define TLSLAVE_BASE 0x82000000L


static inline int tlslave_write_req(uint64_t offset) {
  reg_write64(TLSLAVE_BASE + offset, 0xdeadbeafdeadbeafL);
}
