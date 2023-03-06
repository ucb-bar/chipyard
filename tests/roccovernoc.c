#include "rocc.h"
#include <stdio.h>

void __main(void)
{
  unsigned long hartid = 0;
  asm volatile ("csrr %0, mhartid" : "=r"(hartid));
  unsigned long hart0 = 0;
  while (1) {
    // Write into hart0's register
    ROCC_INSTRUCTION_SS(0, hartid, hart0, 0);
  }
}


// Only hart0 gets here, harts > 0 should will execute the __main code
int main(void) {
  while (1) {
    uint64_t ron_reg;
    ROCC_INSTRUCTION_D(0, ron_reg, 1); // read from hart0's register
    printf("Hart0's RoCCOverNoC register is %lx\n", ron_reg);
  }
}
