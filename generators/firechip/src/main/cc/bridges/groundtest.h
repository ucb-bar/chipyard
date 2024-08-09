// See LICENSE for license details
#ifndef __GROUNDTEST_H
#define __GROUNDTEST_H

#include "core/bridge_driver.h"

struct GROUNDTESTBRIDGEMODULE_struct {
  uint64_t success;
};

class groundtest_t : public bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  groundtest_t(simif_t &sim,
               const std::vector<std::string> &args,
               const GROUNDTESTBRIDGEMODULE_struct &mmio_addrs);
  ~groundtest_t() override;

  void init() override;
  void tick() override;
  bool terminate() override { return _success; }
  int exit_code() override { return 0; }
  void finish() override {}

private:
  bool _success = false;
  const GROUNDTESTBRIDGEMODULE_struct mmio_addrs;
};

#endif
