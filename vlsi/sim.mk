SIM_CONF = $(OBJ_DIR)/sim-inputs.yml
SIM_DEBUG_CONF = $(OBJ_DIR)/sim-debug-inputs.yml
SIM_TIMING_CONF = $(OBJ_DIR)/sim-timing-inputs.yml

.PHONY: $(SIM_CONF) $(SIM_DEBUG_CONF) $(SIM_TIMING_CONF)

$(SIM_CONF): $(sim_common_files)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  tb_name: ''" >> $@  # don't specify -top
	echo "  input_files:" >> $@
	for x in $$(cat $(MODEL_MODS_FILELIST) $(MODEL_BB_MODS_FILELIST) | sort -u) $(MODEL_SMEMS_FILE) $(SIM_FILE_REQS); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  input_files_meta: 'append'" >> $@
	echo "  timescale: '1ns/10ps'" >> $@
	echo "  options:" >> $@
	for x in $(filter-out -f $(sim_common_files),$(VCS_NONCC_OPTS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  options_meta: 'append'" >> $@
	echo "  defines:" >> $@
	for x in $(subst +define+,,$(PREPROC_DEFINES)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  defines_meta: 'append'" >> $@
	echo "  compiler_cc_opts:" >> $@
	for x in $(filter-out "",$(VCS_CXXFLAGS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  compiler_cc_opts_meta: 'append'" >> $@
	echo "  compiler_ld_opts:" >> $@
	for x in $(filter-out "",$(VCS_LDFLAGS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  compiler_ld_opts_meta: 'append'" >> $@
	echo "  execution_flags_prepend: ['$(PERMISSIVE_ON)']" >> $@
	echo "  execution_flags_append: ['$(PERMISSIVE_OFF)']" >> $@
	echo "  execution_flags:" >> $@
	for x in $(SIM_FLAGS); do \
	  echo '    - "'$$x'"' >> $@; \
	done
	echo "  execution_flags_meta: 'append'" >> $@
ifneq ($(BINARY), )
	echo "  benchmarks: ['$(BINARY)']" >> $@
endif
	echo "  tb_dut: 'TestDriver.testHarness.$(VLSI_MODEL_DUT_NAME)'" >> $@

$(SIM_DEBUG_CONF): $(sim_common_files)
	mkdir -p $(dir $@)
	mkdir -p $(output_dir)
	echo "sim.inputs:" > $@
	echo "  defines: ['DEBUG']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  execution_flags:" >> $@
	for x in $(VERBOSE_FLAGS) $(WAVEFORM_FLAG); do \
	  echo '    - "'$$x'"' >> $@; \
	done
	echo "  execution_flags_meta: 'append'" >> $@
	echo "  saif.mode: 'time'" >> $@
	echo "  saif.start_time: '0ns'" >> $@
	echo "  saif.end_time: '`bc <<< $(timeout_cycles)*$(CLOCK_PERIOD)`ns'" >> $@
ifndef USE_VPD
	echo "  options:" >> $@
	echo '    - "-kdb"' >> $@
	echo "  options_meta: 'append'" >> $@
	echo "sim.outputs.waveforms: ['$(sim_out_name).fsdb']" >> $@
else
	echo "sim.outputs.waveforms: ['$(sim_out_name).vpd']" >> $@
endif

$(SIM_TIMING_CONF): $(sim_common_files)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  defines: ['NTC']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  timing_annotated: 'true'" >> $@

# Update hammer top-level sim targets to include our generated sim configs
redo-sim-rtl: $(SIM_CONF)
redo-sim-rtl-$(VLSI_TOP): $(SIM_CONF)
redo-sim-rtl: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-rtl-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-rtl: override HAMMER_SIM_RUN_DIR = sim-rtl-rundir
redo-sim-rtl-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = sim-rtl-$(VLSI_TOP)
redo-sim-rtl-debug: $(SIM_DEBUG_CONF) redo-sim-rtl
redo-sim-rtl-debug-$(VLSI_TOP): $(SIM_DEBUG_CONF) redo-sim-rtl-$(VLSI_TOP)
redo-sim-rtl-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-rtl-debug-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)

redo-sim-syn: $(SIM_CONF)
redo-sim-syn-$(VLSI_TOP): $(SIM_CONF)
redo-sim-syn: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-syn-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-syn: override HAMMER_SIM_RUN_DIR = sim-syn-rundir
redo-sim-syn-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = sim-syn-$(VLSI_TOP)
redo-sim-syn-debug: $(SIM_DEBUG_CONF) redo-sim-syn
redo-sim-syn-debug-$(VLSI_TOP): $(SIM_DEBUG_CONF) redo-sim-syn-$(VLSI_TOP)
redo-sim-syn-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-syn-debug-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-syn-timing-debug: $(SIM_TIMING_CONF) redo-sim-syn-debug
redo-sim-syn-timing-debug-$(VLSI_TOP): $(SIM_TIMING_CONF) redo-sim-syn-debug-$(VLSI_TOP)
redo-sim-syn-timing-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
redo-sim-syn-timing-debug-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

redo-sim-par: $(SIM_CONF)
redo-sim-par-$(VLSI_TOP): $(SIM_CONF)
redo-sim-par: override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-par-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_CONF)
redo-sim-par: override HAMMER_SIM_RUN_DIR = sim-par-rundir
redo-sim-par-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = sim-par-$(VLSI_TOP)
redo-sim-par-debug: $(SIM_DEBUG_CONF) redo-sim-par
redo-sim-par-debug-$(VLSI_TOP): $(SIM_DEBUG_CONF) redo-sim-par-$(VLSI_TOP)
redo-sim-par-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-par-debug-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
redo-sim-par-timing-debug: $(SIM_TIMING_CONF) redo-sim-par-debug
redo-sim-par-timing-debug-$(VLSI_TOP): $(SIM_TIMING_CONF) redo-sim-par-debug-$(VLSI_TOP)
redo-sim-par-timing-debug: override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
redo-sim-par-timing-debug-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

sim-rtl: $(SIM_CONF)
sim-rtl-$(VLSI_TOP): $(SIM_CONF)
sim-rtl: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-rtl-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-rtl: override HAMMER_SIM_RUN_DIR = sim-rtl-rundir
sim-rtl-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = sim-rtl-$(VLSI_TOP)
sim-rtl-debug: $(SIM_DEBUG_CONF) sim-rtl
sim-rtl-debug-$(VLSI_TOP): $(SIM_DEBUG_CONF) sim-rtl-$(VLSI_TOP)
sim-rtl-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-rtl-debug-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)

sim-syn: $(SIM_CONF)
sim-syn-$(VLSI_TOP): $(SIM_CONF)
sim-syn: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-syn-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-syn: override HAMMER_SIM_RUN_DIR = sim-syn-rundir
sim-syn-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = sim-syn-$(VLSI_TOP)
sim-syn-debug: $(SIM_DEBUG_CONF) sim-syn
sim-syn-debug-$(VLSI_TOP): $(SIM_DEBUG_CONF) sim-syn-$(VLSI_TOP)
sim-syn-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-syn-debug-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-syn-timing-debug: $(SIM_TIMING_CONF) sim-syn-debug
sim-syn-timing-debug-$(VLSI_TOP): $(SIM_TIMING_CONF) sim-syn-debug-$(VLSI_TOP)
sim-syn-timing-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
sim-syn-timing-debug-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

sim-par: $(SIM_CONF)
sim-par-$(VLSI_TOP): $(SIM_CONF)
sim-par: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-par-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_CONF)
sim-par: override HAMMER_SIM_RUN_DIR = sim-par-rundir
sim-par-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = sim-par-$(VLSI_TOP)
sim-par-debug: $(SIM_DEBUG_CONF) sim-par
sim-par-debug-$(VLSI_TOP): $(SIM_DEBUG_CONF) sim-par-$(VLSI_TOP)
sim-par-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-par-debug-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_DEBUG_CONF)
sim-par-timing-debug: $(SIM_TIMING_CONF) sim-par-debug
sim-par-timing-debug-$(VLSI_TOP): $(SIM_TIMING_CONF) sim-par-debug-$(VLSI_TOP)
sim-par-timing-debug: override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)
sim-par-timing-debug-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(SIM_TIMING_CONF)

$(OBJ_DIR)/sim-%/sim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)
