#########################################################################################
# xcelium makefile
#########################################################################################

define CAD_INFO_HEADER
# --------------------------------------------------------------------------------
# This script was written and developed by Chipyard at UC Berkeley; however, the
# underlying commands and reports are copyrighted by Cadence. We thank Cadence for
# granting permission to share our research to help promote and foster the next
# generation of innovators.
# --------------------------------------------------------------------------------
endef

export CAD_INFO_HEADER

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
sim_name = xrun

#########################################################################################
# vcs simulator types and rules
#########################################################################################
sim_prefix = simx
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim)-debug
sim_workdir = $(sim).xcelium.d
sim_run_tcl = $(sim)_run.tcl
sim_debug_run_tcl = $(sim_debug)_run.tcl

include $(base_dir)/xcelium.mk

.PHONY: default debug
default: $(sim)
debug: $(sim_debug)

#########################################################################################
# simulation requirements
#########################################################################################
SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/TestDriver.v

# copy files but ignore *.h files in *.f since xcelium has -Wcxx include
$(sim_files): $(SIM_FILE_REQS) $(ALL_MODS_FILELIST) | $(GEN_COLLATERAL_DIR)
	cp -f $(SIM_FILE_REQS) $(GEN_COLLATERAL_DIR)
	$(foreach file,\
		$(SIM_FILE_REQS),\
		$(if $(filter %.h,$(file)),\
			,\
			echo "$(addprefix $(GEN_COLLATERAL_DIR)/, $(notdir $(file)))" >> $@;))

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# vcs binary and arguments
#########################################################################################
VCS = xrun -64bit -elaborate
#vcs -full64

VCS_OPTS = $(VCS_CC_OPTS) $(VCS_NONCC_OPTS) $(PREPROC_DEFINES)

#########################################################################################
# vcs build paths
#########################################################################################
model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

#########################################################################################
# vcs simulator rules
#########################################################################################

$(sim_workdir): $(sim_common_files) $(dramsim_lib) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir)
	$(VCS) $(VCS_OPTS) $(EXTRA_SIM_SOURCES) -xmlibdirname $@

$(sim_run_tcl): $(sim_workdir)
	echo "$$CAD_INFO_HEADER" > $(sim_run_tcl)
	echo "run" >> $(sim_run_tcl)
	echo "exit" >> $(sim_run_tcl)

$(sim): $(sim_workdir) $(sim_run_tcl)
	echo "#!/usr/bin/env bash" > $(sim)
	echo "$$CAD_INFO_HEADER" >> $(sim)
	echo "LD_PRELOAD=$(base_dir)/.conda-env/lib/libstdc++.so.6 xrun +verbose +binary=super-awesome-program +permissive -R -xmlibdirname $(sim_workdir) -input $(sim_run_tcl)" >> $(sim)
	chmod +x $(sim)

$(sim_debug_run_tcl): $(sim_workdir)
	echo "$$CAD_INFO_HEADER" > $(sim_debug_run_tcl)
	echo "database -open default_vcd_dump -vcd -into $(sim_out_name).vcd" >> $(sim_debug_run_tcl)
	echo "set probe_packed_limit 64k" >> $(sim_debug_run_tcl)
	echo "probe -create $(TB) -database default_vcd_dump -depth all -all" >> $(sim_debug_run_tcl)
	echo "run" >> $(sim_debug_run_tcl)
	echo "database -close default_vcd_dump" >> $(sim_debug_run_tcl)
	echo "exit" >> $(sim_debug_run_tcl)


$(sim_debug): $(sim_workdir) $(sim_debug_run_tcl)
	echo "#!/usr/bin/env bash" > $(sim_debug)
	echo "$$CAD_INFO_HEADER" >> $(sim_debug)
	echo "LD_PRELOAD=$(base_dir)/.conda-env/lib/libstdc++.so.6 xrun +verbose +binary=super-awesome-program +permissive -R -xmlibdirname $(sim_workdir) -input $(sim_debug_run_tcl)" >> $(sim_debug)
	chmod +x $(sim_debug)


#########################################################################################
# create vcs vpd/fsdb rules
#########################################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vpd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) +vcdplusfile=$@ $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)

.PRECIOUS: $(output_dir)/%.fsdb %.fsdb
$(output_dir)/%.fsdb: $(output_dir)/% $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) +fsdbfile=$@ $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)


#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean clean-sim clean-sim-debug
clean:
	rm -rf $(gen_dir) $(sim_prefix)-* ucli.key

clean-sim:
	rm -rf $(model_dir) $(build_dir)/vc_hdrs.h $(sim) $(sim).daidir ucli.key

clean-sim-debug:
	rm -rf $(model_dir_debug) $(build_dir)/vc_hdrs.h $(sim_debug) $(sim_debug).daidir ucli.key
