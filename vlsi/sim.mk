.PHONY: $(SIM_CONF) $(SIM_DEBUG_CONF) $(SIM_TIMING_CONF)
# Update hammer top-level sim targets to include our generated sim configs
redo-sim: $(SIM_CONF)
redo-sim: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-debug: $(SIM_DEBUG_CONF) redo-sim
redo-sim-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)

redo-sim-syn: $(SIM_CONF)
redo-sim-syn: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-syn-debug: $(SIM_DEBUG_CONF) redo-sim-syn
redo-sim-syn-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)

redo-sim-par: $(SIM_CONF)
redo-sim-par: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-par-debug: $(SIM_DEBUG_CONF) redo-sim-par
redo-sim-par-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-par-timing-debug: $(SIM_TIMING_CONF) redo-sim-par-debug
redo-sim-par-timing-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

sim: $(SIM_CONF)
sim: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-debug: $(SIM_DEBUG_CONF) sim
sim-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
$(OBJ_DIR)/sim-rundir/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)

sim-syn: $(SIM_CONF)
sim-syn: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-syn-debug: $(SIM_DEBUG_CONF) sim-syn
sim-syn-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
$(OBJ_DIR)/sim-syn-rundir/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)

sim-par: $(SIM_CONF)
sim-par: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-par-debug: $(SIM_DEBUG_CONF) sim-par
sim-par-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-par-timing-debug: $(SIM_TIMING_CONF) sim-par-debug
sim-par-timing-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
$(OBJ_DIR)/sim-par-rundir/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)
