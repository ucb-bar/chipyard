##############################################################
# extra variables/targets ingested by the chipyard make system
##############################################################

AXE_DIR=$(base_dir)/tools/axe/src
AXE=$(AXE_DIR)/axe

$(AXE): $(wildcard $(AXE_DIR)/*.[ch]) $(AXE_DIR)/make.sh
	cd $(AXE_DIR) && ./make.sh

$(output_dir)/tracegen.out: $(SIM_PREREQ)
	mkdir -p $(output_dir) && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) none </dev/null 2> $@

$(output_dir)/tracegen.result: $(output_dir)/tracegen.out $(AXE)
	$(base_dir)/scripts/check-tracegen.sh $< > $@

.PHONY: tracegen
tracegen: $(output_dir)/tracegen.result
