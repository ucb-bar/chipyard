// See LICENSE for license details.

#include "BridgeHarness.h"

class UARTModuleTest final : public BridgeHarness {
public:
  using BridgeHarness::BridgeHarness;

private:
  unsigned get_step_limit() const override { return 300000; }
  unsigned get_tick_limit() const override { return 100000; }
};
TEST_MAIN(UARTModuleTest)
