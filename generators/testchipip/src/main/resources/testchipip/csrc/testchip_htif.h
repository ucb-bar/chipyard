#ifndef __TESTCHIP_HTIF_H
#define __TESTCHIP_HTIF_H

#include <stdexcept>
#include <stdint.h>
#include <vector>
#include <string>
#include <fesvr/htif.h>

struct init_access_t {
  uint64_t address;
  uint32_t stdata;
  bool store;
};

class testchip_htif_t
{
 public:
  virtual void write_chunk(addr_t taddr, size_t nbytes, const void* src) = 0;
  virtual void read_chunk(addr_t taddr, size_t nbytes, void* dst) = 0;
  virtual ~testchip_htif_t() {};

 protected:
  void perform_init_accesses();
  void parse_htif_args(std::vector<std::string> &args);
  bool write_hart0_msip = true;
  std::vector<init_access_t> init_accesses;
};
#endif
