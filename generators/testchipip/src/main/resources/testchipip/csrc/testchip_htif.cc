#include "testchip_htif.h"

void testchip_htif_t::parse_htif_args(std::vector<std::string> &args) {
  for (auto& arg : args) {
    if (arg.find("+init_write=0x") == 0) {
      auto d = arg.find(":0x");
      if (d == std::string::npos) {
        throw std::invalid_argument("Improperly formatted +init_write argument");
      }
      uint64_t addr = strtoull(arg.substr(14, d - 14).c_str(), 0, 16);
      uint32_t val = strtoull(arg.substr(d + 3).c_str(), 0, 16);
      init_access_t access = { .address=addr, .stdata=val, .store=true };
      init_accesses.push_back(access);
    }
    if (arg.find("+init_read=0x") == 0) {
      uint64_t addr = strtoull(arg.substr(13).c_str(), 0, 16);
      init_access_t access = { .address=addr, .stdata=0, .store=false };
      init_accesses.push_back(access);
    }
    if (arg.find("+no_hart0_msip") == 0)
      write_hart0_msip = false;
  }
}

void testchip_htif_t::perform_init_accesses() {
  for (auto p : init_accesses) {
    if (p.store) {
      fprintf(stderr, "Writing %lx with %x\n", p.address, p.stdata);
      write_chunk(p.address, sizeof(uint32_t), &p.stdata);
      fprintf(stderr, "Done writing %lx with %x\n", p.address, p.stdata);
    } else {
      fprintf(stderr, "Reading %lx ...", p.address);
      uint32_t rdata = 0;
      read_chunk(p.address, sizeof(uint32_t), &rdata);
      fprintf(stderr, " got %x\n", rdata);
    }
  }
}
