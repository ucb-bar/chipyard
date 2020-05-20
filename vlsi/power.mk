.PHONY: $(POWER_CONF)
power: $(POWER_CONF) sim-par
power: override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF)
redo-power: $(POWER_CONF)
redo-power: override HAMMER_EXTRA_ARGS += -p $(POWER_CONF)
$(OBJ_DIR)/power-rundir/power-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_POWER_EXTRA_ARGS)
