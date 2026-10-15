// See LICENSE for license details.

#include "bridges/jtagbridge.h"

#include <cstdlib>
#include <cstring>
#include <iostream>

char jtagbridge_t::KIND;

static const int DEFAULT_RBB_PORT = 25000;
static const char PORT_ARG_PREFIX[] = "+jtag_rbb_port=";

jtagbridge_t::jtagbridge_t(simif_t &simif,
                           const JTAGBRIDGEMODULE_struct &mmio_addrs,
                           int jtagno,
                           const std::vector<std::string> &args)
    : bridge_driver_t(simif, &KIND),
      mmio_addrs_(mmio_addrs),
      rbb_(nullptr),
      rbb_port_(DEFAULT_RBB_PORT + jtagno) {
  for (const auto &arg : args) {
    if (arg.find(PORT_ARG_PREFIX) == 0) {
      rbb_port_ = std::atoi(arg.c_str() + strlen(PORT_ARG_PREFIX));
    }
  }
}

jtagbridge_t::~jtagbridge_t() { delete rbb_; }

void jtagbridge_t::init() {
  rbb_ = new remote_bitbang_t((uint16_t)rbb_port_);
  std::cout << "[JTAGBridge] remote_bitbang listening on port " << rbb_port_
            << std::endl;
}

// Semantics of one bridge tick:
//   - Consumes exactly one remote_bitbang command byte from the client (a pin
//     update '0'..'7', a 'R' TDO sample, or 'Q'); this advances the target by
//     one token.
//   - Reads the sampled TDO from the target (one MMIO read) and writes back the
//     TCK/TMS/TDI pin state the client requested (three MMIO writes).
// A single tick is therefore one pin update or one TDO sample, NOT a complete
// JTAG bit: a full bit takes at least a TCK-low and a TCK-high command, plus a
// separate 'R' when the client wants to read TDO. The remote_bitbang client owns
// all TAP sequencing and samples TDO (via 'R') at the point it considers stable
// (conventionally while TCK is low, before driving the next rising edge).
void jtagbridge_t::tick() {
  if (!rbb_)
    return;

  unsigned char tck = 0, tms = 0, tdi = 0, trstn = 0;
  unsigned char tdo = (unsigned char)read(mmio_addrs_.tdo);

  rbb_->tick(&tck, &tms, &tdi, &trstn, tdo);

  write(mmio_addrs_.tck, tck);
  write(mmio_addrs_.tms, tms);
  write(mmio_addrs_.tdi, tdi);
}
