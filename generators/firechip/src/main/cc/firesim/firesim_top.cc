// See LICENSE for license details

#include "bridges/clock.h"
#include "bridges/fased_memory_timing_model.h"
#include "bridges/heartbeat.h"
#include "bridges/peek_poke.h"
#include "core/bridge_driver.h"
#include "core/simif.h"
#include "core/simulation.h"
#include "core/systematic_scheduler.h"

class firesim_top_t : public systematic_scheduler_t, public simulation_t {
public:
  firesim_top_t(simif_t &simif,
                widget_registry_t &registry,
                const std::vector<std::string> &args);

  int simulation_run() override;

  bool simulation_timed_out() override { return !terminated; }

private:
  simif_t &simif;
  /// Reference to the peek-poke bridge.
  peek_poke_t &peek_poke;
  /// Flag to indicate that the simulation was terminated.
  bool terminated = false;
};

firesim_top_t::firesim_top_t(simif_t &simif,
                             widget_registry_t &registry,
                             const std::vector<std::string> &args)
    : systematic_scheduler_t(args), simulation_t(registry, args), simif(simif),
      peek_poke(registry.get_widget<peek_poke_t>()) {

  // Cycles to advance before profiling instrumentation registers in models.
  std::optional<uint64_t> profile_interval;
  for (auto &arg : args) {
    if (arg.find("+profile-interval=") == 0) {
      profile_interval = atoi(arg.c_str() + 18);

      if (*profile_interval == 0) {
        fprintf(stderr, "Must provide a profile interval > 0\n");
        exit(1);
      }
    }
  }

  registry.add_widget(
      new heartbeat_t(simif, registry.get_widget<clockmodule_t>(), args));

  // Add functions you'd like to periodically invoke on a paused simulator here.
  if (profile_interval) {
    register_task(
        0, [&, profile_interval] { // capture profile_interval by value,
                                   //  since its lifetime is bound to
                                   //  firesim_top_t's constructor
          for (auto &mod : registry.get_bridges<FASEDMemoryTimingModel>()) {
            mod->profile();
          }
          return *profile_interval;
        });
  }
}

int firesim_top_t::simulation_run() {
  int exit_code = 0;
  while (!terminated && !finished_scheduled_tasks()) {
    run_scheduled_tasks();
    peek_poke.step(get_largest_stepsize(), false);
    while (!peek_poke.is_done() && !terminated) {
      for (auto *bridge : registry.get_all_bridges()) {
        bridge->tick();
        if (bridge->terminate()) {
          exit_code = bridge->exit_code();
          terminated = true;
          break;
        }
      }
    }
  }
  return exit_code;
}

std::unique_ptr<simulation_t>
create_simulation(simif_t &simif,
                  widget_registry_t &registry,
                  const std::vector<std::string> &args) {
  return std::make_unique<firesim_top_t>(simif, registry, args);
}
