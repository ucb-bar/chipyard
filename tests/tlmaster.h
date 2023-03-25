#define TLMASTER_BASE 0x40000L



static inline int tlmaster_send_write_req(void) {
  reg_write64(TLMASTER_BASE, 0xdeadbeaf);
}

static inline int tlmaster_send_read_req(void) {
  reg_read64(TLMASTER_BASE);
}
