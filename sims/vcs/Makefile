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

PERMISSIVE_ON=+permissive
PERMISSIVE_OFF=+permissive-off

WAVEFORM_FLAG=+vcdplusfile=$(sim_out_name).vpd

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

VCS_CC_OPTS = \
	-CC "-I$(VCS_HOME)/include" \
	-CC "-I$(RISCV)/include" \
	-CC "-std=c++11" \
	$(RISCV)/lib/libfesvr.a

VCS_NONCC_OPTS = \
	+lint=all,noVCDE,noONGS,noUI \
	-error=PCWM-L \
	-timescale=1ns/10ps \
	-quiet \
	-q \
	+rad \
	+v2k \
	+vcs+lic+wait \
	+vc+list \
	-f $(sim_common_files) \
	-sverilog \
	+incdir+$(build_dir) \
	+define+CLOCK_PERIOD=1.0 \
	$(sim_vsrcs) \
	+define+PRINTF_COND=$(TB).printf_cond \
	+define+STOP_COND=!$(TB).reset \
	+define+RANDOMIZE_MEM_INIT \
	+define+RANDOMIZE_REG_INIT \
	+define+RANDOMIZE_GARBAGE_ASSIGN \
	+define+RANDOMIZE_INVALID_ASSIGN \
	+libext+.v

VCS_OPTS = -notice -line $(VCS_CC_OPTS) $(VCS_NONCC_OPTS)

#########################################################################################
# vcs simulator rules
#########################################################################################
$(sim): $(sim_vsrcs) $(sim_common_files)
	rm -rf csrc && $(VCS) $(VCS_OPTS) -o $@ \
	-debug_pp

$(sim_debug) : $(sim_vsrcs) $(sim_common_files)
	rm -rf csrc && $(VCS) $(VCS_OPTS) -o $@ \
	+define+DEBUG -debug_pp

#########################################################################################
# create a vcs vpd rule
#########################################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vpd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(SIM_FLAGS) $(VERBOSE_FLAGS) +vcdplusfile=$@ $(PERMISSIVE_OFF) $< 3>&1 1>&2 2>&3 | spike-dasm > $<.out)

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir) csrc $(sim_prefix)-* ucli.key vc_hdrs.h
