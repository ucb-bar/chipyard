// See LICENSE for license details.

#ifndef __JTAGBRIDGE_H
#define __JTAGBRIDGE_H

#include "bridges/remote_bitbang.h"
#include "core/bridge_driver.h"
#include "core/simif.h"

#include <string>
#include <vector>

struct JTAGBRIDGEMODULE_struct {
  uint64_t tck;
  uint64_t tms;
  uint64_t tdi;
  uint64_t tdo;
};

class jtagbridge_t final : public bridge_driver_t {
public:
  static char KIND;

  jtagbridge_t(simif_t &simif,
               const JTAGBRIDGEMODULE_struct &mmio_addrs,
               int jtagno,
               const std::vector<std::string> &args);
  ~jtagbridge_t();

  virtual void init() override;
  virtual void tick() override;
  virtual bool terminate() override { return rbb_ && rbb_->done(); }
  virtual int exit_code() override { return rbb_ ? rbb_->exit_code() : 0; }

private:
  const JTAGBRIDGEMODULE_struct mmio_addrs_;
  remote_bitbang_t *rbb_;
  int rbb_port_;
};

#endif // __JTAGBRIDGE_H
