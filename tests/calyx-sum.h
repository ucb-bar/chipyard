#include <stdio.h>
#include "mmio.h"

#define CALYX_SUM_BASE 0x5000
#define CALYX_SUM_ENQ_RDY  (CALYX_SUM_BASE + 0)
#define CALYX_SUM_ENQ_BITS (CALYX_SUM_BASE + 4)
#define CALYX_SUM_DEQ_VAL  (CALYX_SUM_BASE + 8)
#define CALYX_SUM_DEQ_BITS (CALYX_SUM_BASE + 12)


static inline int calyx_sum_enq_ready() {
  int rdy = reg_read32(CALYX_SUM_ENQ_RDY);
  printf("calyx_sum_enq_ready: %d\n", rdy);
  return (rdy != 0);
}

static inline void calyx_sum_send_input(int val) {
  while (!calyx_sum_enq_ready());
  printf("sending input: %d\n", val);
  reg_write32(CALYX_SUM_ENQ_BITS, val & 0xf);
  printf("sending input done\n");
}

static inline int calyx_sum_deq_valid() {
  int val = reg_read32(CALYX_SUM_DEQ_VAL); 
  printf("calyx_sum_deq_val: %d\n", val);
  return (val != 0);
}

static inline int calyx_sum_get_output() {
  while (!calyx_sum_deq_valid());
  return reg_read32(CALYX_SUM_DEQ_BITS);
}

