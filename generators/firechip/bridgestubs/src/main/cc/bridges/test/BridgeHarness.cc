// See LICENSE for license details.

#include "BridgeHarness.h"

#include "bridges/blockdev.h"
#include "bridges/tracerv.h"
#include "bridges/uart.h"
#include "core/bridge_driver.h"

BridgeHarness::BridgeHarness(widget_registry_t &registry,
                             const std::vector<std::string> &args)
    : simulation_t(registry, args),
      peek_poke(registry.get_widget<peek_poke_t>()) {}

BridgeHarness::~BridgeHarness() = default;

int BridgeHarness::simulation_run() {
  // Reset the DUT.
  peek_poke.poke("reset", 1, /*blocking=*/true);
  peek_poke.step(1, /*blocking=*/true);
  peek_poke.poke("reset", 0, /*blocking=*/true);
  peek_poke.step(1, /*blocking=*/true);

  // Tick until all requests are serviced.
  peek_poke.step(get_step_limit(), /*blocking=*/false);
  for (unsigned i = 0; i < get_tick_limit() && !peek_poke.is_done(); ++i) {
    for (auto &bridge : registry.get_all_bridges()) {
      bridge->tick();
    }
  }

  // Cleanup.
  return EXIT_SUCCESS;
}
