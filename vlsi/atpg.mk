ATPG_CONF = $(OBJ_DIR)/atpg-inputs.yml
ATPG_DEBUG_CONF = $(OBJ_DIR)/atpg-debug-inputs.yml

ATPG_USE_SYN ?= 1
ATPG_SYN_NETLISTS ?= $(OBJ_DIR)/syn-rundir/results/ChipTop.mapped.v
ATPG_LIB ?= /data/libraries/NangateOpenCellLibrary_PDKv1_3_v2010_12/Front_End/Verilog/NangateOpenCellLibrary_fixed.v
ATPG_SYN_SPF ?= $(OBJ_DIR)/syn-rundir/results/ChipTop_test_protocol.spf
ATPG_SYN_DEPS := $(ATPG_SYN_NETLISTS)

.PHONY: ensure-syn
# ensure-syn will run synthesis only if one or more synthesized netlist files are missing
ensure-syn:
	@set -e; \
	files="$(ATPG_SYN_NETLISTS)"; \
	if [ -z "$$files" ]; then \
		echo "ATPG: no synthesized netlists configured (ATPG_SYN_NETLISTS empty), running synthesis (sim-syn)"; \
		$(MAKE) sim-syn; \
	else \
		missing=0; \
		for f in $$files; do \
			if [ ! -f "$$f" ]; then missing=1; break; fi; \
		done; \
		if [ $$missing -eq 1 ]; then \
			echo "ATPG: synthesized netlists missing, running synthesis (sim-syn)"; \
			$(MAKE) sim-syn; \
		else \
			echo "ATPG: synthesized netlists present, skipping synthesis"; \
		fi; \
	fi

.PHONY: $(ATPG_CONF) $(ATPG_DEBUG_CONF)

$(ATPG_CONF): $(sim_common_files) ensure-syn $(ATPG_SYN_DEPS)
	mkdir -p $(dir $@)
	echo "atpg.inputs:" > $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  tb_name: ''" >> $@  # don't specify -top
	echo "  tb_dut: 'TestDriver.testHarness.$(VLSI_MODEL_DUT_NAME)'" >> $@
	echo "  input_files:" >> $@
	echo "    - '$(ATPG_LIB)' " >> $@
	echo "  input_files_meta: 'append'" >> $@
	echo "  spf_file : '$(ATPG_SYN_SPF)'" >> $@
	echo "  options:" >> $@
	for x in $(filter-out -f $(sim_common_files),$(VCS_NONCC_OPTS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  options_meta: 'append'" >> $@
	echo "  defines:" >> $@
	for x in $(subst +define+,,$(SIM_PREPROC_DEFINES)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  defines_meta: 'append'" >> $@

$(ATPG_DEBUG_CONF): $(sim_common_files)
	mkdir -p $(dir $@)
	mkdir -p $(output_dir)
	echo "atpg.inputs:" > $@
	echo "  defines:" >> $@
	echo "    - 'DEBUG'" >> $@;
ifndef USE_VPD
	echo "    - 'FSDB=1'" >> $@;
endif
	echo "  defines_meta: 'append'" >> $@
	echo "  options:" >> $@
	echo '    - "-kdb"' >> $@
	echo "  options_meta: 'append'" >> $@

# ATPG targets that forward generated config to hammer
# Ensure synthesis (sim-syn) runs before ATPG so ATPG only runs when synth exists.
atpg-syn: $(ATPG_CONF)
atpg-syn-$(VLSI_TOP): $(ATPG_CONF)
atpg-syn: override HAMMER_ATPG_EXTRA_ARGS += -p $(ATPG_CONF)
atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_EXTRA_ARGS += -p $(ATPG_CONF)
atpg-syn: override HAMMER_ATPG_RUN_DIR = atpg-syn-rundir
atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_RUN_DIR = atpg-syn-$(VLSI_TOP)

redo-atpg-syn: $(ATPG_CONF)
redo-atpg-syn-$(VLSI_TOP): $(ATPG_CONF)
redo-atpg-syn: override HAMMER_EXTRA_ARGS += -p $(ATPG_CONF)
redo-atpg-syn-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(ATPG_CONF)
redo-atpg-syn: override HAMMER_ATPG_RUN_DIR = atpg-syn-rundir
redo-atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_RUN_DIR = atpg-syn-$(VLSI_TOP)

$(OBJ_DIR)/atpg-%/atpg-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_ATPG_EXTRA_ARGS)

# Backwards-compatible aliases: `make atpg` -> `make atpg-syn`
.PHONY: atpg atpg-$(VLSI_TOP)
atpg: atpg-syn
atpg-$(VLSI_TOP): atpg-syn-$(VLSI_TOP)
