#ifndef __ROUTER_H__
#define __ROUTER_H__

#include <stdio.h>
#include <stdint.h>
#include "mmio.h"

#define ROUTER_MMIO             0x4000
#define CHIP_ID_ADDR            0x4080
#define ROUTING_MODE_ADDR       0x4088
#define BYPASS_PORT_ADDR        0x4090
#define TRANSLATION_MODE_ADDR   0x4098
#define TRANSLATION_TABLE_ADDR  0x40A0

static inline void program_router(uint64_t table_entry, uint64_t chip_id, uint64_t port) {
  uint64_t base = ROUTER_MMIO + table_entry * 32;
  reg_write64(base + 0,  1);        // valid
  reg_write64(base + 8,  chip_id);  // chipID
  reg_write64(base + 16, port);     // port
}

static inline void set_bypass_mode(uint8_t port) {
  reg_write64(ROUTING_MODE_ADDR, 0);   // bypass mode
  reg_write64(BYPASS_PORT_ADDR, port);
  printf("Bypass mode: port %d\n", port);
}

static inline void write_translation_table(uint8_t entry, uint64_t id) {
  reg_write64(TRANSLATION_TABLE_ADDR + entry * 8, id);
  printf("Wrote translation table entry %d: 0x%lx\n", entry, id);
}

#endif
