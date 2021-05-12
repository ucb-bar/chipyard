#########################################################################################
# verilator makefile
#########################################################################################
ifeq ($(shell which verilator),)
$(error Did not find Verilator in PATH. Make sure all requirements are installed)
endif

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

WAVEFORM_FLAG=-v$(sim_out_name).vcd

include $(base_dir)/sims/common-sim-flags.mk

# If verilator seed unspecified, verilator uses srand as random seed
ifdef RANDOM_SEED
SEED_FLAG=+verilator+seed+I$(RANDOM_SEED)
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
	$(CHIPYARD_RSRCS_DIR)/csrc/emulator.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/verilator.h \

# the following files are needed for emulator.cc to compile
SIM_FILE_REQS += \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/SimSerial.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/testchip_tsi.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/testchip_tsi.h \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/SimDRAM.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm.h \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm_dramsim2.h \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm_dramsim2.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/SimDTM.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/SimJTAG.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/remote_bitbang.h \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/remote_bitbang.cc

# copy files and add -FI for *.h files in *.f
$(sim_files): $(SIM_FILE_REQS) | $(build_dir)
	cp -f $^ $(build_dir)
	$(foreach file,\
		$^,\
		$(if $(filter %.h,$(file)),\
			echo "-FI $(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;,\
			echo "$(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;))

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
"   VERILATOR_FST_MODE     = enable FST waveform instead of VCD. use with debug build"

#########################################################################################
# verilator/cxx binary and flags
#########################################################################################
VERILATOR := verilator --cc --exe

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

VERILATOR_FST_MODE ?= 0
TRACING_OPTS := $(if $(filter $(VERILATOR_FST_MODE),0),\
	                  --trace,--trace-fst --trace-threads 1)
TRACING_CFLAGS := $(if $(filter $(VERILATOR_FST_MODE),0),,-DCY_FST_TRACE)

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
	if grep -qiP "module\s+(CVA6|NVDLA)" $(build_dir)/*.*v; \
	then echo "$(VERILOG_IP_VERILATOR_FLAGS)"; \
	else echo "$(CHIPYARD_VERILATOR_FLAGS)"; fi)

# Use --timescale to approximate timescale behavior of pre-4.034
TIMESCALE_OPTS := $(shell verilator --version | perl -lne 'if (/(\d.\d+)/ && $$1 >= 4.034) { print "--timescale 1ns/1ps"; }')

# see: https://github.com/ucb-bar/riscv-mini/issues/31
MAX_WIDTH_OPTS = $(shell verilator --version | perl -lne 'if (/(\d.\d+)/ && $$1 > 4.016) { print "--max-num-width 1048576"; }')

PREPROC_DEFINES := \
	+define+PRINTF_COND=\$$c\(\"verbose\",\"\&\&\"\,\"done_reset\"\) \
	+define+STOP_COND=\$$c\(\"done_reset\"\)

VERILATOR_NONCC_OPTS = \
	$(RUNTIME_PROFILING_VFLAGS) \
	$(RUNTIME_THREADS) \
	$(VERILATOR_OPT_FLAGS) \
	$(PLATFORM_OPTS) \
	-Wno-fatal \
	$(TIMESCALE_OPTS) \
	$(MAX_WIDTH_OPTS) \
	$(PREPROC_DEFINES) \
	--top-module $(VLOG_MODEL) \
	--vpi \
	-f $(sim_common_files) \
	$(sim_vsrcs)

#----------------------------------------------------------------------------------------
# gcc configuration/optimization
#----------------------------------------------------------------------------------------
VERILATOR_CXXFLAGS = \
	$(SIM_CXXFLAGS) \
	$(RUNTIME_PROFILING_CFLAGS) \
	$(TRACING_CFLAGS) \
	-D__STDC_FORMAT_MACROS \
	-DTEST_HARNESS=V$(VLOG_MODEL) \
	-DVERILATOR \
	-include $(build_dir)/$(long_name).plusArgs \
	-include $(build_dir)/verilator.h

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

model_header = $(model_dir)/V$(VLOG_MODEL).h
model_header_debug = $(model_dir_debug)/V$(VLOG_MODEL).h

model_mk = $(model_dir)/V$(VLOG_MODEL).mk
model_mk_debug = $(model_dir_debug)/V$(VLOG_MODEL).mk

#########################################################################################
# build makefile fragment that builds the verilator sim rules
#########################################################################################
$(model_mk): $(sim_vsrcs) $(sim_common_files) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir)
	mkdir -p $(model_dir)
	$(VERILATOR) $(VERILATOR_OPTS) $(EXTRA_SIM_SOURCES) -o $(sim) -Mdir $(model_dir) -CFLAGS "-include $(model_header)"
	touch $@

$(model_mk_debug): $(sim_vsrcs) $(sim_common_files) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir_debug)
	mkdir -p $(model_dir_debug)
	$(VERILATOR) $(VERILATOR_OPTS) $(EXTRA_SIM_SOURCES) -o $(sim_debug) $(TRACING_OPTS) -Mdir $(model_dir_debug) -CFLAGS "-include $(model_header_debug)"
	touch $@

#########################################################################################
# invoke make to make verilator sim rules
#########################################################################################
$(sim): $(model_mk) $(dramsim_lib)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir) -f V$(VLOG_MODEL).mk

$(sim_debug): $(model_mk_debug) $(dramsim_lib)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir_debug) -f V$(VLOG_MODEL).mk

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
	rm -rf $(gen_dir) $(sim_prefix)-*

clean-sim:
	rm -rf $(model_dir) $(sim)

clean-sim-debug:
	rm -rf $(model_dir_debug) $(sim_debug)
