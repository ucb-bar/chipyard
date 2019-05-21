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

PERMISSIVEON=+permissive
PERMISSIVEOFF=+permissive-off

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
	-CC "-Wl,-rpath,$(RISCV)/lib" \
	$(RISCV)/lib/libfesvr.so

VCS_NONCC_OPTS = \
	+lint=all,noVCDE,noONGS,noUI \
	-error=PCWM-L \
	-timescale=1ns/10ps \
	-quiet \
	+rad \
	+v2k \
	+vcs+lic+wait \
	+vc+list \
	-f $(sim_top_blackboxes) \
	-f $(sim_harness_blackboxes) \
	-f $(sim_dotf) \
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
$(sim): $(sim_vsrcs) $(sim_dotf)
	rm -rf csrc && $(VCS) $(VCS_OPTS) -o $@ \
	-debug_pp

$(sim_debug) : $(sim_vsrcs) $(sim_dotf)
	rm -rf csrc && $(VCS) $(VCS_OPTS) -o $@ \
	+define+DEBUG -debug_pp

#########################################################################################
# helper rules to run simulator with debug
#########################################################################################
run-binary-debug: $(sim_debug)
	$(sim_debug) $(PERMISSIVEON) $(SIM_FLAGS) +vcdplusfile=$(sim_out_name).vpd $(PERMISSIVEOFF) $(BINARY) 3>&1 1>&2 2>&3 | spike-dasm > $(sim_out_name).out

#########################################################################################
# create a vcs vpd rule
#########################################################################################
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	$(sim_debug) +vcdplusfile=$@ +max-cycles=$(timeout_cycles) $<

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir)/* csrc $(sim_prefix)-* ucli.key vc_hdrs.h
