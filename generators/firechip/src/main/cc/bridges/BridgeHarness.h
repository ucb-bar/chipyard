#ifndef MIDAEXAMPLES_BRIDGEHARNESS_H
#define MIDAEXAMPLES_BRIDGEHARNESS_H

#include "bridges/blockdev.h"
#include "bridges/peek_poke.h"
#include "bridges/uart.h"
#include "core/simif.h"
#include "core/simulation.h"

class bridge_driver_t;

/**
 * Base class for simple unit tests.
 *
 * All bridges from the DUT are registered, initialised and ticked by this
 * harness for a test-specific number of ticks and steps.
 */
class BridgeHarness : public simulation_t {
public:
  BridgeHarness(widget_registry_t &registry,
                const std::vector<std::string> &args);

  ~BridgeHarness() override;

  int simulation_run() override;

protected:
  virtual unsigned get_step_limit() const = 0;
  virtual unsigned get_tick_limit() const = 0;

private:
  peek_poke_t &peek_poke;
};

#define TEST_MAIN(CLASS_NAME)                                                  \
  std::unique_ptr<simulation_t> create_simulation(                             \
      simif_t &simif,                                                          \
      widget_registry_t &registry,                                             \
      const std::vector<std::string> &args) {                                  \
    return std::make_unique<CLASS_NAME>(registry, args);                       \
  }
#endif // MIDAEXAMPLES_BRIDGEHARNESS_H
