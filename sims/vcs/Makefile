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

PREPROC_DEFINES = \
	+define+VCS \
	+define+CLOCK_PERIOD=1.0 \
	+define+PRINTF_COND=$(TB).printf_cond \
	+define+STOP_COND=!$(TB).reset \
	+define+RANDOMIZE_MEM_INIT \
	+define+RANDOMIZE_REG_INIT \
	+define+RANDOMIZE_GARBAGE_ASSIGN \
	+define+RANDOMIZE_INVALID_ASSIGN

VCS_NONCC_OPTS = \
	-notice \
	-line \
	+lint=all,noVCDE,noONGS,noUI \
	-timescale=1ns/1ps \
	-quiet \
	-q \
	+rad \
	+vcs+lic+wait \
	+vc+list \
	-error=noZMMCM \
	-error=PCWM-L \
	-sverilog +systemverilogext+.sv+.svi+.svh+.svt -assert svaext +libext+.sv \
	+v2k +verilog2001ext+.v95+.vt+.vp +libext+.v \
	+incdir+$(build_dir) \
	$(PREPROC_DEFINES) \
	-f $(sim_common_files) \
	$(sim_vsrcs)

#----------------------------------------------------------------------------------------
# gcc configuration/optimization
#----------------------------------------------------------------------------------------
# -flto slows down compilation on small-memory and breaks on firesim-manager
CMODE := -O3 -fbranch-probabilities -march=native

VCS_CXXFLAGS = \
	$(CXXFLAGS) \
	$(CMODE) \
	-I$(VCS_HOME)/include \
	-I$(RISCV)/include \
	-I$(dramsim_dir) \
	-std=c++11 \
	$(EXTRA_SIM_CXXFLAGS)

VCS_LDFLAGS =	\
	$(LDFLAGS) \
	$(CMODE) \
	-L$(RISCV)/lib \
	-Wl,-rpath,$(RISCV)/lib \
	-L$(sim_dir) \
	-L$(dramsim_dir) \
	-lfesvr \
	-ldramsim \
	$(EXTRA_SIM_LDFLAGS)

VCS_CC_OPTS = \
	-CFLAGS "$(VCS_CXXFLAGS)" \
	-LDFLAGS "$(VCS_LDFLAGS)"

#----------------------------------------------------------------------------------------
# full vcs+gcc opts
#----------------------------------------------------------------------------------------
VCS_OPTS = $(VCS_CC_OPTS) $(VCS_NONCC_OPTS)

#########################################################################################
# vcs simulator rules
#########################################################################################
$(sim): $(sim_vsrcs) $(sim_common_files) $(dramsim_lib) $(EXTRA_SIM_REQS)
	rm -rf csrc && $(VCS) $(VCS_OPTS) $(EXTRA_SIM_SOURCES) -o $@ \
	-debug_pp

$(sim_debug): $(sim_vsrcs) $(sim_common_files) $(dramsim_lib) $(EXTRA_SIM_REQS)
	rm -rf csrc && $(VCS) $(VCS_OPTS) $(EXTRA_SIM_SOURCES) -o $@ \
	+define+DEBUG \
	-debug_pp

#########################################################################################
# create a vcs vpd rule
#########################################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vpd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) +vcdplusfile=$@ $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean clean-sim clean-sim-debug
clean:
	rm -rf $(gen_dir) $(sim_prefix)-*

clean-sim:
	rm -rf csrc/ $(sim) ucli.key vc_hdrs.h

clean-sim-debug:
	rm -rf csrc/ $(sim_debug) ucli.key vc_hdrs.h

#########################################################################################
# print help text
#########################################################################################
.PHONY: help
help:
	@for line in $(HELP_LINES); do echo "$$line"; done
