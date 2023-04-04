#ifndef _ROCC_H
#define _ROCC_H

#include <riscv/extension.h>
#include <riscv/rocc.h>
#include <random>
#include <limits>

#include <riscv/extension.h>
//Need to include anything else here?

class generic_t : public extension_t
{
  public:
    const char* name() { return "generic" ; }
    
    reg_t custom3(rocc_insn_t insn, reg_t xs1, reg_t xs2);

    virtual std::vector<insn_desc_t> get_instructions();
    virtual std::vector<disasm_insn_t*> get_disasms();
    
    void reset();

   protected:
    //Anything needs to go? Lol what sorts of things need to be protected? Abstracted traits?    
};

#endif
