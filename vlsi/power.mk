.PHONY: $(POWER_CONF)
power-par: $(POWER_CONF) sim-par
power-par: override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF)
redo-power-par: $(POWER_CONF)
redo-power-par: override HAMMER_EXTRA_ARGS += -p $(POWER_CONF)
$(OBJ_DIR)/power-rundir/power-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_POWER_EXTRA_ARGS)
