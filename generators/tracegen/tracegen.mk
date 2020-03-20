AXE_DIR=$(base_dir)/tools/axe/src
AXE=$(AXE_DIR)/axe

$(AXE): $(wildcard $(AXE_DIR)/*.[ch]) $(AXE_DIR)/make.sh
	cd $(AXE_DIR) && ./make.sh

$(output_dir)/tracegen.out: $(sim)
	mkdir -p $(output_dir) && $(sim) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) none </dev/null 2> $@

$(output_dir)/tracegen.result: $(output_dir)/tracegen.out $(AXE)
	$(base_dir)/scripts/check-tracegen.sh $< > $@

.PHONY: tracegen
tracegen: $(output_dir)/tracegen.result

##################################################################
# THE FOLLOWING MUST BE += operators
##################################################################

# sourced used to run the generator
PROJECT_GENERATOR_SOURCES +=

# simargs needed (i.e. like +drj_test=hello)
PROJECT_SIM_FLAGS +=

# extra vcs compile flags
PROJECT_VCS_FLAGS +=

# extra verilator compile flags
PROJECT_VERILATOR_FLAGS +=

# extra simulation sources needed for VCS/Verilator compile
PROJECT_SIM_SOURCES +=
