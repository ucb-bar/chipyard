// See LICENSE for license details.

#include "BridgeHarness.h"

class BlockDevModuleTest final : public BridgeHarness {
public:
  using BridgeHarness::BridgeHarness;

private:
  unsigned get_step_limit() const override { return 30000; }
  unsigned get_tick_limit() const override { return 3000; }
};
TEST_MAIN(BlockDevModuleTest)
