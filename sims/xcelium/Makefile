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
# xcelium simulator types and rules
#########################################################################################
sim_prefix = simx
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim)-debug
sim_workdir = $(build_dir)/xcelium.d
sim_run_tcl = $(build_dir)/xcelium_run.tcl
sim_debug_run_tcl = $(build_dir)/xcelium_debug_run.tcl

include $(base_dir)/sims/xcelium/xcelium.mk

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
# xcelium binary and arguments
#########################################################################################
XCELIUM = xrun
XCELIUM_OPTS = $(XCELIUM_CC_OPTS) $(XCELIUM_NONCC_OPTS) $(PREPROC_DEFINES)

#########################################################################################
# xcelium build paths
#########################################################################################
model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

#########################################################################################
# xcelium simulator rules
#########################################################################################

$(sim_workdir): $(sim_common_files) $(dramsim_lib) $(EXTRA_SIM_REQS)
	$(call require_cmd,xrun)
	$(require_riscv)
	rm -rf $(model_dir)
	$(XCELIUM) -elaborate $(XCELIUM_OPTS) $(EXTRA_SIM_SOURCES) $(XCELIUM_COMMON_ARGS)

$(sim_run_tcl): $(sim_workdir)
	echo "$$CAD_INFO_HEADER" > $@
	echo "run" >> $@
	echo "exit" >> $@

# The system libstdc++ may not link correctly with some of our dynamic libs, so
# force loading the conda one (if present) with LD_PRELOAD
$(sim): $(sim_workdir) $(sim_run_tcl)
	echo "#!/usr/bin/env bash" > $@
	echo "$$CAD_INFO_HEADER" >> $@
	cat arg-reshuffle >> $@
	echo "LD_PRELOAD=$(CONDA_PREFIX)/lib/libstdc++.so.6 $(XCELIUM) +permissive -R -input $(sim_run_tcl) $(XCELIUM_COMMON_ARGS) +permissive-off \$$INPUT_ARGS" >> $@
	chmod +x $@

$(sim_debug_run_tcl): $(sim_workdir)
	echo "$$CAD_INFO_HEADER" > $@
	echo "database -open default_vcd_dump -vcd -into \$$env(XCELIUM_WAVEFORM_FLAG)" >> $@
	echo "set probe_packed_limit 64k" >> $@
	echo "probe -create $(TB) -database default_vcd_dump -depth all -all" >> $@
	echo "run" >> $@
	echo "database -close default_vcd_dump" >> $@
	echo "exit" >> $@

$(sim_debug): $(sim_workdir) $(sim_debug_run_tcl)
	echo "#!/usr/bin/env bash" > $@
	echo "$$CAD_INFO_HEADER" >> $@
	cat arg-reshuffle >> $@
	echo "export XCELIUM_WAVEFORM_FLAG=\$$XCELIUM_WAVEFORM_FLAG" >> $@
	echo "LD_PRELOAD=$(CONDA_PREFIX)/lib/libstdc++.so.6 $(XCELIUM) +permissive -R -input $(sim_debug_run_tcl) $(XCELIUM_COMMON_ARGS) +permissive-off \$$INPUT_ARGS" >> $@
	chmod +x $@

#########################################################################################
# create vcd rules
#########################################################################################
.PRECIOUS: $(output_dir)/%.vcd %.vcd
$(output_dir)/%.vcd: $(output_dir)/% $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) +vcdplusfile=$@ $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean clean-sim clean-sim-debug
clean:
	rm -rf $(CLASSPATH_CACHE) $(gen_dir) $(sim_prefix)-*

clean-sim:
	rm -rf $(model_dir) $(sim) $(sim_workdir) $(sim_run_tcl) ucli.key bpad_*.err sigusrdump.out dramsim*.log

clean-sim-debug:
	rm -rf $(model_dir_debug) $(sim_debug) $(sim_workdir) $(sim_debug_run_tcl) ucli.key bpad_*.err sigusrdump.out dramsim*.log
