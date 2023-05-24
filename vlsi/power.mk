POWER_CONF = $(OBJ_DIR)/power-inputs.yml
POWER_RTL_CONF = $(OBJ_DIR)/power-rtl-inputs.yml
POWER_SYN_CONF = $(OBJ_DIR)/power-syn-inputs.yml
POWER_PAR_CONF = $(OBJ_DIR)/power-par-inputs.yml
POWER_PAR_HIER_CONF = $(OBJ_DIR)/power-par-$(VLSI_TOP)-inputs.yml

.PHONY: $(POWER_CONF) $(POWER_RTL_CONF) $(POWER_SYN_CONF) $(POWER_PAR_CONF) $(POWER_PAR_HIER_CONF)

$(POWER_CONF): $(VLSI_RTL) check-binary
	mkdir -p $(dir $@)
	echo "power.inputs:" > $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  tb_name: TestDriver" >> $@
	echo "  tb_dut: 'testHarness/$(VLSI_MODEL_DUT_NAME)'" >> $@
ifneq ($(BINARY), )
	echo "  waveforms: [" >> $@
ifndef USE_VPD
	echo "    '$(call get_sim_out_name,$(BINARY)).fsdb'" >> $@
else
	echo "    '$(call get_sim_out_name,$(BINARY)).vpd'" >> $@
endif
	echo "  ]" >> $@
endif
	echo "  start_times: ['0ns']" >> $@
	echo "  end_times: [" >> $@
	echo "    '`bc <<< $(timeout_cycles)*$(CLOCK_PERIOD)`ns'" >> $@
	echo "  ]" >> $@

$(POWER_RTL_CONF): $(VLSI_RTL)
	echo "vlsi.core.power_tool: hammer.power.joules" > $@
	echo "power.inputs:" >> $@
	echo "  level: rtl" >> $@
	echo "  input_files:" >> $@
	for x in $$(cat $(VLSI_RTL)); do \
		echo '    - "'$$x'"' >> $@; \
	done

$(POWER_SYN_CONF): $(VLSI_RTL)
	echo "vlsi.core.power_tool: hammer.power.joules" > $@
	echo "power.inputs:" >> $@
	echo "  level: syn" >> $@

$(POWER_PAR_CONF): $(VLSI_RTL)
	echo "vlsi.core.power_tool: hammer.power.voltus" > $@
	echo "power.inputs:" >> $@
	echo "  level: par" >> $@
	echo "  database: '$(OBJ_DIR)/par-rundir/$(VLSI_TOP)_FINAL'" >> $@

$(POWER_PAR_HIER_CONF): $(VLSI_RTL)
	echo "vlsi.core.power_tool: hammer.power.voltus" > $@
	echo "power.inputs:" >> $@
	echo "  level: par" >> $@
	echo "  database: '$(OBJ_DIR)/par-$(VLSI_TOP)/$(VLSI_TOP)_FINAL'" >> $@

power-rtl: $(POWER_CONF) $(POWER_RTL_CONF) sim-rtl-debug
power-rtl-$(VLSI_TOP): $(POWER_CONF) $(POWER_RTL_CONF) sim-rtl-debug-$(VLSI_TOP)
power-rtl: override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_RTL_CONF)
power-rtl-$(VLSI_TOP): override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_RTL_CONF)
redo-power-rtl: $(POWER_CONF) $(POWER_RTL_CONF)
redo-power-rtl-$(VLSI_TOP): $(POWER_CONF) $(POWER_RTL_CONF)
redo-power-rtl: override HAMMER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_RTL_CONF)
redo-power-rtl-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_RTL_CONF)

power-syn: $(POWER_CONF) $(POWER_SYN_CONF) sim-syn-debug
power-syn-$(VLSI_TOP): $(POWER_CONF) $(POWER_SYN_CONF) sim-syn-debug-$(VLSI_TOP)
power-syn: override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_SYN_CONF)
power-syn-$(VLSI_TOP): override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_SYN_CONF)
redo-power-syn: $(POWER_CONF) $(POWER_SYN_CONF)
redo-power-syn-$(VLSI_TOP): $(POWER_CONF) $(POWER_SYN_CONF)
redo-power-syn: override HAMMER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_SYN_CONF)
redo-power-syn-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_SYN_CONF)

power-par: $(POWER_CONF) $(POWER_PAR_CONF) sim-par-debug
power-par-$(VLSI_TOP): $(POWER_CONF) $(POWER_PAR_HIER_CONF) sim-par-debug-$(VLSI_TOP)
power-par: override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_PAR_CONF)
power-par-$(VLSI_TOP): override HAMMER_POWER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_PAR_HIER_CONF)
redo-power-par: $(POWER_CONF) $(POWER_PAR_CONF)
redo-power-par-$(VLSI_TOP): $(POWER_CONF) $(POWER_PAR_HIER_CONF)
redo-power-par: override HAMMER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_PAR_CONF)
redo-power-par-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(POWER_CONF) -p $(POWER_PAR_HIER_CONF)

$(OBJ_DIR)/power-%/power-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_POWER_EXTRA_ARGS)
