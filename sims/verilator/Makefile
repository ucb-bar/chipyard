#########################################################################################
# verilator makefile
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
sim_name = verilator

#########################################################################################
# verilator simulator types and rules
#########################################################################################
sim_prefix = simulator
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)-debug

include $(base_dir)/sims/common-sim-flags.mk

# If verilator seed unspecified, verilator uses srand as random seed
ifdef RANDOM_SEED
SEED_FLAG=+verilator+seed+$(RANDOM_SEED)
else
SEED_FLAG=
endif

.PHONY: default debug
default: $(sim)
debug: $(sim_debug)

#########################################################################################
# simulaton requirements
#########################################################################################
SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/TestDriver.v

# copy files and add -FI for *.h files in *.f
$(sim_files): $(SIM_FILE_REQS) $(ALL_MODS_FILELIST) | $(GEN_COLLATERAL_DIR)
	cp -f $(SIM_FILE_REQS) $(GEN_COLLATERAL_DIR)
	$(foreach file,\
		$(SIM_FILE_REQS),\
		$(if $(filter %.h,$(file)),\
			echo "-FI $(addprefix $(GEN_COLLATERAL_DIR)/, $(notdir $(file)))" >> $@;,\
			echo "$(addprefix $(GEN_COLLATERAL_DIR)/, $(notdir $(file)))" >> $@;))

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# verilator-specific user-interface variables and commands
#########################################################################################
HELP_COMPILATION_VARIABLES += \
"   VERILATOR_PROFILE      = 'none' if no verilator profiling (default)" \
"                            'all' if full verilator runtime profiling" \
"                            'threads' if runtime thread profiling only" \
"   VERILATOR_THREADS      = how many threads the simulator will use (default 1)" \
"   USE_FST                = set to '1' to build Verilator simulator to emit FST instead of VCD."

HELP_SIMULATION_VARIABLES += \
"   USE_FST                = set to '1' to run Verilator simulator emitting FST instead of VCD." \
"   DUMP_TMA               = set to '1' to dump TMA performance counters at simulation end FOR BOOM ONLY"

# BOOM TMA SUPPORT
DUMP_TMA ?= 0
EXTRA_SIM_FLAGS += $(if $(filter $(DUMP_TMA),0),,+dump-tma-counters)

# WF_SCOPES: space-separated list of hierarchy paths to limit `$dumpvars` to.
# Up to 8 scopes. Empty (default) → dump entire testHarness (legacy behavior).
# Example: make debug CONFIG=SmallBoomWaveConfig WF_SCOPES="testHarness.chiptop0 testHarness.chiptop0.system"
# Note: Verilator binds dump scopes at compile time. After changing WF_SCOPES,
#       run `make clean-sim-debug` before rebuilding the debug simulator.
WF_SCOPES ?=
WF_SCOPES_DEFINES :=
ifneq ($(strip $(WF_SCOPES)),)
WF_SCOPES_DEFINES += +define+WF_SCOPES_ENABLED
WF_SCOPES_DEFINES += $(foreach i,1 2 3 4 5 6 7 8,$(if $(word $(i),$(WF_SCOPES)),+define+WF_SCOPE_$(i)=$(word $(i),$(WF_SCOPES))))
endif

# Stamp file that captures the current WF_SCOPES value. Force-checked on every
# make invocation; only its mtime is bumped when WF_SCOPES actually changes.
# model_mk_debug depends on it so changing WF_SCOPES auto-triggers a rebuild.
WF_STAMP := $(sim_dir)/.wf_scopes.stamp
.PHONY: wf_stamp_check
wf_stamp_check:
	@new_val='$(WF_SCOPES)'; \
	old_val=$$(cat $(WF_STAMP) 2>/dev/null || echo "__UNSET__"); \
	if [ "$$new_val" != "$$old_val" ]; then \
		echo "$$new_val" > $(WF_STAMP); \
	fi
$(WF_STAMP): wf_stamp_check ;

#########################################################################################
# verilator/cxx binary and flags
#########################################################################################
VERILATOR := verilator --main --timing --cc --exe
# Debug build uses our custom main (sim_dir/wf_main.cc) instead of --main so we
# can capture the model pointer and bridge a DPI-C function that toggles the
# trace stream's __Vm_dumping flag (Verilator drops $dumpoff/$dumpon).
VERILATOR_DEBUG := verilator --timing --cc --exe
WF_DEBUG_MAIN := $(sim_dir)/wf_main.cc

#----------------------------------------------------------------------------------------
# user configs
#----------------------------------------------------------------------------------------
VERILATOR_PROFILE ?= none
RUNTIME_PROFILING_CFLAGS := $(if $(filter $(VERILATOR_PROFILE),all),-g -pg,)
RUNTIME_PROFILING_VFLAGS := $(if $(filter $(VERILATOR_PROFILE),all),\
                              --prof-threads --prof-cfuncs,\
                              $(if $(filter $(VERILATOR_PROFILE),threads),\
								--prof-threads,))

VERILATOR_THREADS ?= 1
RUNTIME_THREADS := --threads $(VERILATOR_THREADS) --threads-dpi all

USE_FST ?= 0
TRACING_OPTS := $(if $(filter $(USE_FST),0),\
	                  --trace,--trace-fst --trace-threads 1)
# TODO: consider renaming +vcdfile in TestDriver.v to +waveformfile (or similar)
get_waveform_flag = +vcdfile=$(1).$(if $(filter $(USE_FST),0),vcd,fst)

#----------------------------------------------------------------------------------------
# verilation configuration/optimization
#----------------------------------------------------------------------------------------
# we initially had --noassert for performance, but several modules use
# assertions, including dramsim, so we enable --assert by default
VERILATOR_OPT_FLAGS ?= \
	-O3 \
	--x-assign fast \
	--x-initial fast \
	--output-split 10000 \
	--output-split-cfuncs 100

# default flags added for external IP (cva6/NVDLA)
VERILOG_IP_VERILATOR_FLAGS := \
	--unroll-count 256 \
	-Wno-PINCONNECTEMPTY \
	-Wno-ASSIGNDLY \
	-Wno-DECLFILENAME \
	-Wno-UNUSED \
	-Wno-UNOPTFLAT \
	-Wno-BLKANDNBLK \
	-Wno-style \
	-Wall

# normal flags used for chipyard builds (that are incompatible with vlog ip aka cva6/NVDLA)
CHIPYARD_VERILATOR_FLAGS := \
	--assert

# options dependent on whether external IP (cva6/NVDLA) or just chipyard is used
# NOTE: defer the evaluation of this until it is used!
PLATFORM_OPTS = $(shell \
	if grep -qiP "module\s+(CVA6|NVDLA|Ara)" $(GEN_COLLATERAL_DIR)/*.*v; \
	then echo "$(VERILOG_IP_VERILATOR_FLAGS)"; \
	else echo "$(CHIPYARD_VERILATOR_FLAGS)"; fi)

# Use --timescale to approximate timescale behavior of pre-4.034
TIMESCALE_OPTS := $(shell verilator --version | perl -lne 'if (/(\d.\d+)/ && $$1 >= 4.034) { print "--timescale 1ns/1ps"; }')

# see: https://github.com/ucb-bar/riscv-mini/issues/31
MAX_WIDTH_OPTS = $(shell verilator --version | perl -lne 'if (/(\d.\d+)/ && $$1 > 4.016) { print "--max-num-width 1048576"; }')

VERILATOR_PREPROC_DEFINES = \
	+define+VERILATOR

VERILATOR_NONCC_OPTS = \
	$(RUNTIME_PROFILING_VFLAGS) \
	$(RUNTIME_THREADS) \
	$(VERILATOR_OPT_FLAGS) \
	$(PLATFORM_OPTS) \
	-Wno-fatal \
	$(TIMESCALE_OPTS) \
	$(MAX_WIDTH_OPTS) \
	$(SIM_PREPROC_DEFINES) \
	$(VERILATOR_PREPROC_DEFINES) \
	--top-module $(TB) \
	--vpi \
	$(addprefix +incdir+,$(EXT_INCDIRS)) \
	-f $(sim_common_files)

#----------------------------------------------------------------------------------------
# gcc configuration/optimization
#----------------------------------------------------------------------------------------
VERILATOR_CXXFLAGS = \
	$(SIM_CXXFLAGS) \
	$(RUNTIME_PROFILING_CFLAGS) \
	-DVERILATOR

VERILATOR_LDFLAGS = $(SIM_LDFLAGS)

VERILATOR_CC_OPTS = \
	-CFLAGS "$(VERILATOR_CXXFLAGS)" \
	-LDFLAGS "$(VERILATOR_LDFLAGS)"

#----------------------------------------------------------------------------------------
# full verilator+gcc opts
#----------------------------------------------------------------------------------------
VERILATOR_OPTS = $(VERILATOR_CC_OPTS) $(VERILATOR_NONCC_OPTS)

#########################################################################################
# verilator build paths and file names
#########################################################################################
model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

model_header = $(model_dir)/V$(TB).h
model_header_debug = $(model_dir_debug)/V$(TB).h

model_mk = $(model_dir)/V$(TB).mk
model_mk_debug = $(model_dir_debug)/V$(TB).mk

#########################################################################################
# build makefile fragment that builds the verilator sim rules
#########################################################################################

$(model_mk): $(sim_common_files) $(EXTRA_SIM_REQS)
	$(call require_cmd,verilator)
	rm -rf $(model_dir)
	mkdir -p $(model_dir)
	$(VERILATOR) $(VERILATOR_OPTS) $(EXTRA_SIM_SOURCES) -o $(sim) -Mdir $(model_dir)
	touch $@

$(model_mk_debug): $(sim_common_files) $(EXTRA_SIM_REQS) $(WF_DEBUG_MAIN) $(WF_STAMP)
	$(call require_cmd,verilator)
	rm -rf $(model_dir_debug)
	mkdir -p $(model_dir_debug)
	$(sim_dir)/wf_pragma_inject.py $(GEN_COLLATERAL_DIR) "$(WF_SCOPES)"
	$(VERILATOR_DEBUG) $(VERILATOR_OPTS) +define+DEBUG $(WF_SCOPES_DEFINES) $(EXTRA_SIM_SOURCES) $(WF_DEBUG_MAIN) -o $(sim_debug) $(TRACING_OPTS) -Mdir $(model_dir_debug)
	touch $@

#########################################################################################
# invoke make to make verilator sim rules
#########################################################################################
$(sim): $(model_mk) $(dramsim_lib)
	$(require_riscv)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir) -f V$(TB).mk

$(sim_debug): $(model_mk_debug) $(dramsim_lib)
	$(require_riscv)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir_debug) -f V$(TB).mk

#########################################################################################
# create a verilator vpd rule
#########################################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vcd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	(set -o pipefail && $(NUMA_PREFIX) $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) -v$@.vcd $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean clean-sim clean-sim-debug
clean:
	rm -rf $(CLASSPATH_CACHE) $(gen_dir) $(sim_prefix)-*

clean-sim:
	rm -rf $(model_dir) $(sim)

clean-sim-debug:
	rm -rf $(model_dir_debug) $(sim_debug)
