#define TLMASTER_BASE 0x40000L



static inline int tlmaster_send_req(void) {
  reg_write64(TLMASTER_BASE, 0xdeadbeaf);
}

