#include <inttypes.h>
#include <assert.h>




#define TLMASTER_BASE 0x40000L





static inline int tlmaster_send_write_req(uint64_t offset) {
  assert((offset & 0x7) == 0);
  reg_write64(TLMASTER_BASE + offset, 0xdeadbeaf);
}

static inline int tlmaster_send_read_req(uint64_t offset) {
  assert((offset & 0x7) == 0);
  reg_read64(TLMASTER_BASE + offset);
}
