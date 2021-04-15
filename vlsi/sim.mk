.PHONY: $(SIM_CONF) $(SIM_DEBUG_CONF) $(SIM_TIMING_CONF)
# Update hammer top-level sim targets to include our generated sim configs
redo-sim-rtl: $(SIM_CONF)
redo-sim-rtl: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-rtl: override HAMMER_SIM_RUN_DIR = sim-rtl-rundir
redo-sim-rtl-debug: $(SIM_DEBUG_CONF) redo-sim-rtl
redo-sim-rtl-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)

redo-sim-syn: $(SIM_CONF)
redo-sim-syn: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-syn: override HAMMER_SIM_RUN_DIR = sim-syn-rundir
redo-sim-syn-debug: $(SIM_DEBUG_CONF) redo-sim-syn
redo-sim-syn-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-syn-timing-debug: $(SIM_TIMING_CONF) redo-sim-syn-debug
redo-sim-syn-timing-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

redo-sim-par: $(SIM_CONF)
redo-sim-par: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-par: override HAMMER_SIM_RUN_DIR = sim-par-rundir
redo-sim-par-debug: $(SIM_DEBUG_CONF) redo-sim-par
redo-sim-par-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-par-timing-debug: $(SIM_TIMING_CONF) redo-sim-par-debug
redo-sim-par-timing-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

sim-rtl: $(SIM_CONF)
sim-rtl: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-rtl: override HAMMER_SIM_RUN_DIR = sim-rtl-rundir
sim-rtl-debug: $(SIM_DEBUG_CONF) sim-rtl
sim-rtl-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
$(OBJ_DIR)/sim-rtl-rundir/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)

sim-syn: $(SIM_CONF)
sim-syn: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-syn: override HAMMER_SIM_RUN_DIR = sim-syn-rundir
sim-syn-debug: $(SIM_DEBUG_CONF) sim-syn
sim-syn-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-syn-timing-debug: $(SIM_TIMING_CONF) sim-syn-debug
sim-syn-timing-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
$(OBJ_DIR)/sim-syn-rundir/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)

sim-par: $(SIM_CONF)
sim-par: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-par: override HAMMER_SIM_RUN_DIR = sim-par-rundir
sim-par-debug: $(SIM_DEBUG_CONF) sim-par
sim-par-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-par-timing-debug: $(SIM_TIMING_CONF) sim-par-debug
sim-par-timing-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
$(OBJ_DIR)/sim-par-rundir/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)
