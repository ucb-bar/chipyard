ATPG_CONF = $(OBJ_DIR)/atpg-inputs.yml

.PHONY: $(ATPG_CONF)

$(ATPG_CONF):
	@mkdir -p $(dir $@)
	@echo "atpg.inputs:" > $@
	@echo "  top_module: $(VLSI_TOP)" >> $@
	@echo "  tb_name: ''" >> $@  # don't specify -top
	@echo "  tb_dut: ''" >> $@
	@echo "  input_files:" >> $@
	@echo "    - '' " >> $@
	@echo "  input_files_meta: 'append'" >> $@
ifdef FAULT_MODEL
	@echo "  fault_model: '$(FAULT_MODEL)'" >> $@
endif
ifdef PATTERNS_FILE
	@echo "  patterns_file: '$(PATTERNS_FILE)'" >> $@
endif
ifdef FAULTS_FILE
	@echo "  faults_file: '$(FAULTS_FILE)'" >> $@
endif

# ATPG targets that forward generated config to hammer
# Ensure synthesis (sim-syn) runs before ATPG so ATPG only runs when synth exists.
atpg-syn: $(ATPG_CONF)
atpg-syn-$(VLSI_TOP): $(ATPG_CONF)
atpg-syn: override HAMMER_ATPG_EXTRA_ARGS += -p $(ATPG_CONF) -p $(vlsi_dir)/$(TOOLS_CONF)
atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_EXTRA_ARGS += -p $(ATPG_CONF) -p $(vlsi_dir)/$(TOOLS_CONF)
atpg-syn: override HAMMER_ATPG_RUN_DIR = atpg-syn-rundir
atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_RUN_DIR = atpg-syn-$(VLSI_TOP)

redo-atpg-syn: $(ATPG_CONF) syn-to-atpg
redo-atpg-syn-$(VLSI_TOP): $(ATPG_CONF) syn-to-atpg
redo-atpg-syn: override HAMMER_EXTRA_ARGS += -p $(ATPG_CONF) -p $(vlsi_dir)/$(TOOLS_CONF)
redo-atpg-syn-$(VLSI_TOP): override HAMMER_EXTRA_ARGS += -p $(ATPG_CONF) -p $(vlsi_dir)/$(TOOLS_CONF)
redo-atpg-syn: override HAMMER_ATPG_EXTRA_ARGS += -p $(ATPG_CONF) -p $(vlsi_dir)/$(TOOLS_CONF)
redo-atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_EXTRA_ARGS += -p $(ATPG_CONF) -p $(vlsi_dir)/$(TOOLS_CONF)
redo-atpg-syn: override HAMMER_ATPG_RUN_DIR = atpg-syn-rundir
redo-atpg-syn-$(VLSI_TOP): override HAMMER_ATPG_RUN_DIR = atpg-syn-$(VLSI_TOP)

$(OBJ_DIR)/atpg-syn-input.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_ATPG_EXTRA_ARGS)
$(OBJ_DIR)/atpg-syn-input.json: $(ATPG_CONF)

$(OBJ_DIR)/atpg-%/atpg-output-full.json: private override HAMMER_EXTRA_ARGS += $(HAMMER_ATPG_EXTRA_ARGS)

# Backwards-compatible aliases: `make atpg` -> `make atpg-syn`
.PHONY: atpg atpg-$(VLSI_TOP)
atpg: atpg-syn
atpg-$(VLSI_TOP): atpg-syn-$(VLSI_TOP)
