FSIM_CONF = $(OBJ_DIR)/fsim-inputs.yml
# ToDo: output of FSIM in defined folder
FSIM_CAMPAIGN_DUT ?= TestDriver.testHarness.$(VLSI_MODEL_DUT_NAME)
FSIM_STROBE_FILE ?= $(OBJ_DIR)/../../fsim-utilities/strobe.sv
FSIM_CAMPAIGN_TCL ?= $(OBJ_DIR)/../../fsim-utilities/fsim.tcl
FAULT_TYPE ?= tf
SFF_FILE ?= $(OBJ_DIR)/../../fsim-utilities/gen_$(FAULT_TYPE)_$(VLSI_MODEL_DUT_NAME).sff
FSIM_OUTPUT_FOLDER ?= $(OBJ_DIR)/../../fsim-output/

.PHONY: $(FSIM_CONF)

$(FSIM_CONF): $(sim_common_files) check-binary
	mkdir -p $(dir $@)
	echo "fsim.gui: $(SIM_USE_GUI)" > $@
	echo "fsim.inputs:" >> $@
	echo "  campaign_tb_dut: '$(FSIM_CAMPAIGN_DUT)'" >> $@
	echo "  strobe_file_name: '$(FSIM_STROBE_FILE)'" >> $@
	echo "  campaign_tcl: '$(FSIM_CAMPAIGN_TCL)'" >> $@
	echo "  output_folder: '$(FSIM_OUTPUT_FOLDER)'" >> $@
	echo "  sff_file: '$(SFF_FILE)'" >> $@
	echo "  campaign_simv_daidir: 'simv.daidir'" >> $@
	echo "  fault_type: '$(FAULT_TYPE)'" >> $@
	echo "  saif.mode: \"none\" "
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  tb_name: '$(FSIM_CAMPAIGN_DUT)'" >> $@  
	echo "  input_files:" >> $@
	for x in $$(cat $(MODEL_MODS_FILELIST) $(BB_MODS_FILELIST)| uniq | sort -u) $(MODEL_SMEMS_FILE) $(SIM_FILE_REQS); do \
		if echo "$$x" | grep -q "_TestHarness_UNIQUIFIED\.sv$$"; then \
			x_mod=$$(echo "$$x" | sed 's/_TestHarness_UNIQUIFIED\.sv$$/.sv/'); \
		else \
			x_mod="$$x"; \
		fi; \
		echo '    - "'$$x_mod'"' >> $@; \
	done
	echo "  input_files_meta: 'append'" >> $@
	echo "  timescale: '1ns/10ps'" >> $@
	echo "  options:" >> $@
	for x in $(filter-out -f $(sim_common_files),$(VCS_NONCC_OPTS)); do \
		if echo "$$x" | grep -q "+rad$$"; then \
			echo ''; \
		else \
			echo '    - "'$$x'"' >> $@; \
		fi \
	done
	echo "  options_meta: 'append'" >> $@
	echo "  defines:" >> $@
	for x in $(subst +define+,,$(SIM_PREPROC_DEFINES)); do \
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
	for x in $(SIM_FLAGS) $(call get_common_sim_flags,$*) $(VERBOSE_FLAGS); do \
	  echo '    - "'$$x'"' >> $@; \
	done
	echo "  execution_flags_meta: 'append'" >> $@
ifneq ($(BINARY), )
	echo "  benchmarks: ['$(BINARY)']" >> $@
endif
	echo "  tb_dut: 'TestDriver.testHarness.$(VLSI_MODEL_DUT_NAME)'" >> $@
	echo "  core: '$(CONFIG)'" >> $@

redo-fsim-rtl: $(FSIM_CONF)
redo-fsim-rtl-$(VLSI_TOP): $(FSIM_CONF)
redo-fsim-rtl: override HAMMER_EXTRA_ARGS += -p $(FSIM_CONF)
redo-fsim-rtl-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(FSIM_CONF)
redo-fsim-rtl: override HAMMER_SIM_RUN_DIR = fsim-rtl-rundir
redo-fsim-rtl-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = fsim-rtl-$(VLSI_TOP)

redo-fsim-syn: $(FSIM_CONF)
redo-fsim-syn-$(VLSI_TOP): $(FSIM_CONF)
redo-fsim-syn: override HAMMER_EXTRA_ARGS += -p $(FSIM_CONF)
redo-fsim-syn-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(FSIM_CONF)
redo-fsim-syn: override HAMMER_SIM_RUN_DIR = fsim-syn-rundir
redo-fsim-syn-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = fsim-syn-$(VLSI_TOP)

fsim-rtl: $(FSIM_CONF)
fsim-rtl-$(VLSI_TOP): $(FSIM_CONF)
fsim-rtl: override HAMMER_SIM_EXTRA_ARGS += -p $(FSIM_CONF)
fsim-rtl-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(FSIM_CONF)
fsim-rtl: override HAMMER_SIM_RUN_DIR = fsim-rtl-rundir
fsim-rtl-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = fsim-rtl-$(VLSI_TOP)

fsim-syn: $(FSIM_CONF)
fsim-syn-$(VLSI_TOP): $(FSIM_CONF)
fsim-syn: override HAMMER_SIM_EXTRA_ARGS += -p $(FSIM_CONF)
fsim-syn-$(VLSI_TOP): override HAMMER_SIM_EXTRA_ARGS += -p $(FSIM_CONF)
fsim-syn: override HAMMER_SIM_RUN_DIR = fsim-syn-rundir
fsim-syn-$(VLSI_TOP): override HAMMER_SIM_RUN_DIR = fsim-syn-$(VLSI_TOP)

$(OBJ_DIR)/fsim-%/fsim-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_SIM_EXTRA_ARGS)
