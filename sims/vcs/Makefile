#########################################################################################
# vcs makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# name of simulator (used to generate *.f arguments file)
#########################################################################################
sim_name = vcs

#########################################################################################
# vcs simulator types and rules
#########################################################################################
sim_prefix = simv
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)-debug

include $(base_dir)/vcs.mk

.PHONY: default debug
default: $(sim)
debug: $(sim_debug)

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# vcs binary and arguments
#########################################################################################
VCS = vcs -full64

VCS_OPTS = -notice -line $(VCS_CC_OPTS) $(VCS_NONCC_OPTS) $(VCS_DEFINE_OPTS) $(EXTRA_SIM_SOURCES)

#########################################################################################
# vcs simulator rules
#########################################################################################
$(sim): $(sim_vsrcs) $(sim_common_files) $(dramsim_lib) $(EXTRA_SIM_REQS)
	rm -rf csrc && $(VCS) $(VCS_OPTS) -o $@

$(sim_debug): $(sim_vsrcs) $(sim_common_files) $(dramsim_lib) $(EXTRA_SIM_REQS)
	rm -rf csrc && $(VCS) $(VCS_OPTS) -o $@ \
	+define+DEBUG

#########################################################################################
# create a vcs vpd rule
#########################################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vpd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) +vcdplusfile=$@ $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir) csrc $(sim_prefix)-* ucli.key vc_hdrs.h
