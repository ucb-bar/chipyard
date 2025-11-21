ATPG_CONF = $(OBJ_DIR)/atpg-inputs.yml
ATPG_DEBUG_CONF = $(OBJ_DIR)/atpg-debug-inputs.yml

.PHONY: $(ATPG_CONF) $(ATPG_DEBUG_CONF)

$(ATPG_CONF):
	@mkdir -p $(dir $@)
	@echo "atpg.inputs:" > $@
	@echo "  top_module: $(VLSI_TOP)" >> $@
	@echo "  tb_name: ''" >> $@  # don't specify -top
	@echo "  tb_dut: ''" >> $@
	@echo "  input_files:" >> $@
	@echo "    - '' " >> $@
	@echo "  input_files_meta: 'append'" >> $@
	@echo "  options: " >> $@
	@for x in $(filter-out -f $(sim_common_files),$(VCS_NONCC_OPTS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	@echo "  options_meta: 'append'" >> $@
	@echo "  defines:" >> $@
	@for x in $(subst +define+,,$(SIM_PREPROC_DEFINES)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	@echo "  defines_meta: 'append'" >> $@

$(ATPG_DEBUG_CONF):
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
