#ifndef __TESTCHIP_DTM_H
#define __TESTCHIP_DTM_H

#include <string>
#include <fesvr/dtm.h>
#include <vector>
#include <riscv/processor.h>
#include "testchip_htif.h"

struct loadarch_state_t {
  reg_t pc;
  reg_t prv;

  reg_t fcsr;

  reg_t vstart;
  reg_t vxsat;
  reg_t vxrm;
  reg_t vcsr;
  reg_t vtype;

  reg_t stvec;
  reg_t sscratch;
  reg_t sepc;
  reg_t scause;
  reg_t stval;
  reg_t satp;

  reg_t mstatus;
  reg_t medeleg;
  reg_t mideleg;
  reg_t mie;
  reg_t mtvec;
  reg_t mscratch;
  reg_t mepc;
  reg_t mcause;
  reg_t mtval;
  reg_t mip;

  reg_t mcycle;
  reg_t minstret;
  reg_t mtime;
  reg_t mtimecmp;

  reg_t XPR[32];
  reg_t FPR[32];

  reg_t VLEN;
  reg_t ELEN;
  unsigned char* VPR[32];
};

class testchip_dtm_t : public dtm_t, public testchip_htif_t
{
 public:
  testchip_dtm_t(int argc, char** argv, bool has_loadmem);
  virtual ~testchip_dtm_t() {};

  void write_chunk(addr_t taddr, size_t nbytes, const void* src) override;
  void read_chunk(addr_t taddr, size_t nbytes, void* dst) override;
  void load_program() {
    is_loadmem = has_loadmem;
    dtm_t::load_program();
    is_loadmem = false;
  };
  void reset() override;

  std::vector<loadarch_state_t> loadarch_state;
  bool loadarch_done;

 protected:
  virtual void load_mem_write(addr_t taddr, size_t nbytes, const void* src) { };
  virtual void load_mem_read(addr_t taddr, size_t nbytes, void* dst) { };
  bool has_loadmem;

 private:
  bool is_loadmem;
  std::string loadarch_file;

  void loadarch_restore_csr(uint32_t regno, reg_t reg);
  void loadarch_restore_reg(uint32_t regno, reg_t reg);
  void loadarch_restore_freg(uint32_t regno, reg_t reg);
  void loadarch_restore_vreg(uint32_t regno, unsigned char* reg, size_t bytes);
  void loadarch_restore_vtype(uint32_t vtype);
};

#endif
