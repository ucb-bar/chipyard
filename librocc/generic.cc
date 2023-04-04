#include "generic.h"
#include <riscv/mmu.h>
#include <riscv/trap.h>
#include <stdexcept>
#include <iostream>
#include <assert.h>
#include <math.h>
//Any other includes needed?

using namespace std;

REGISTER_EXTENSION(generic, []() { return new generic_t; })

#define dprintf(...) { if (p->get_log_commits_enabled()) printf(__VA_ARGS__); }

reg_t generic_t::custom3(rocc_insn_t insn, reg_t xs1, reg_t xs2) {
  dprintf("ROCC: encountered unknown instruction with funct: %d\n", insn.funct);
  return 0;
}

define_custom_func(generic_t, "generic", generic_custom3, custom3)

std::vector<insn_desc_t> generic_t::get_instructions()
{
  std::vector<insn_desc_t> insns;
  //printf("Received an instruction to generic accelerator, proceeding to fail :)");
  push_custom_insn(insns, ROCC_OPCODE3, ROCC_OPCODE_MASK, ILLEGAL_INSN_FUNC, generic_custom3);
  return insns;
}

std::vector<disasm_insn_t*> generic_t::get_disasms()
{
  std::vector<disasm_insn_t*> insns;
  return insns;
}
