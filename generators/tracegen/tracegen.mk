##############################################################
# extra variables/targets ingested by the chipyard make system
##############################################################

AXE_DIR=$(base_dir)/tools/axe/src
AXE=$(AXE_DIR)/axe

$(AXE): $(wildcard $(AXE_DIR)/*.[ch]) $(AXE_DIR)/make.sh
	cd $(AXE_DIR) && ./make.sh

$(output_dir)/tracegen.out: $(if $(BREAK_SIM_PREREQ),,$(sim))
	mkdir -p $(output_dir) && \
	rm -f $@ $@.tmp && \
	if $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) none </dev/null 2> $@.tmp; then \
	  mv $@.tmp $@; \
	else \
	  cat $@.tmp >&2; \
	  rm -f $@.tmp; \
	  exit 1; \
	fi

$(output_dir)/tracegen.result: $(output_dir)/tracegen.out $(AXE)
	rm -f $@ $@.tmp && \
	if $(base_dir)/scripts/check-tracegen.sh $< > $@.tmp; then \
	  mv $@.tmp $@; \
	else \
	  cat $@.tmp >&2; \
	  rm -f $@.tmp; \
	  exit 1; \
	fi

.PHONY: tracegen
tracegen: $(output_dir)/tracegen.result
