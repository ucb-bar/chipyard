#############################################################################
# Extra verilator-specific Makefile variables:
#
# ENABLE_PRINTF_PATTERN - python regex. any printf() with text that matches
#                         this regex will be force enabled in sim output
#                         default is no selectively enabled printf's
# VERILATOR_PROFILE     - 'none' if no verilator profiling (default)
#                         'all' if full verilator runtime profiling
#                         'threads' if runtime thread profiling only
# VERILATOR_THREADS     - how many threads the simulator will use (default 1)
# VERILATOR_FST_MODE    - enable FST waveform instead of VCD. only makes sense
#                         if debug simulator is built
#############################################################################


#############################################################################
# verilator makefile
#############################################################################
ifeq ($(shell which verilator),)
$(error Did not find Verilator in PATH. Make sure all requirements are installed)
endif

#############################################################################
# general path variables
#############################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

#############################################################################
# include shared variables
#############################################################################
include $(base_dir)/variables.mk

#############################################################################
# name of simulator (used to generate *.f arguments file)
#############################################################################
sim_name = verilator

#############################################################################
# verilator simulator types and rules
#############################################################################
sim_prefix = simulator
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)-debug

WAVEFORM_FLAG=-v$(sim_out_name).vcd

.PHONY: default debug
default: $(sim)
debug: $(sim_debug)

#############################################################################
# import other necessary rules and variables
#############################################################################
include $(base_dir)/common.mk

#############################################################################
# selectively enabling printfs
#############################################################################
ENABLE_PRINTF_PATTERN ?=
sim_vsrcs_printf := $(sim_vsrcs:.v=.printf.v)

#############################################################################
# verilator/cxx binary and flags
#############################################################################
# can't use -Wall, firrtl spits out 1000's of warnings that we can't fix
VERILATOR := verilator --cc --exe

#----------------------------------------------------------------------------
# user configs
#----------------------------------------------------------------------------
VERILATOR_PROFILE ?= none
RUNTIME_PROFILING_CFLAGS := $(if $(filter $(VERILATOR_PROFILE),all),-g -pg,)
RUNTIME_PROFILING_VFLAGS := $(if $(filter $(VERILATOR_PROFILE),all),\
                              --prof-threads --prof-cfuncs,\
                              $(if $(filter $(VERILATOR_PROFILE),threads),\
                                --prof-threads,))

VERILATOR_THREADS ?= 1
RUNTIME_THREADS := --threads $(VERILATOR_THREADS) --threads-dpi all

VERILATOR_FST_MODE ?= 0
TRACING_OPTS := $(if $(filter $(VERILATOR_FST_MODE),0),--trace,--trace-fst-thread)
TRACING_CFLAGS := $(if $(filter $(VERILATOR_FST_MODE),0),,-DCY_FST_TRACE)

#----------------------------------------------------------------------------
# verilation configuration/optimization
#----------------------------------------------------------------------------
# we initially had --noassert for performance, but several modules use 
# assertions, including dramsim, so we enable --assert by default
VMODE := \
	-O3 \
	--x-assign fast \
	--x-initial fast \
	--assert \
  --output-split 10000 \
	--output-split-cfuncs 10000

# default flags added for ariane (-Wno-fatal needed for -Wall to not cause
# a crash, since 1000s of warnings are generated)
ARIANE_VERILATOR_FLAGS := \
	--unroll-count 256 \
	-Werror-PINMISSING \
	-Werror-IMPLICIT \
	-Wno-PINCONNECTEMPTY \
	-Wno-ASSIGNDLY \
	-Wno-DECLFILENAME \
	-Wno-UNUSED \
	-Wno-UNOPTFLAT \
	-Wno-BLKANDNBLK \
	-Wno-style \
	-Wno-fatal \
	-Wall

# normal flags used for chipyard builds (that are incompatible with ariane)
CHIPYARD_VERILATOR_FLAGS := 

# options dependent on whether ariane or chipyard is used
# NOTE: defer the evaluation of this until it is used!
PLATFORM_OPTS = $(shell if ! grep -iq "module.*ariane" $(build_dir)/*.*v; \
                   then echo "$(CHIPYARD_VERILATOR_FLAGS)"; \
                   else echo "$(ARIANE_VERILATOR_FLAGS)"; fi)

# Use --timescale to approximate timescale behavior of pre-4.034
TIMESCALE_OPTS := $(shell verilator --version | perl -lne \
	'if (/(\d.\d+)/ && $$1 >= 4.034) { print "--timescale 1ns/1ps"; }')

PREPROC_DEFINES := \
	+define+PRINTF_COND=\$$c\(\"verbose\",\"\&\&\"\,\"done_reset\"\) \
	+define+STOP_COND=\$$c\(\"done_reset\"\) 

VERILATOR_NONCC_OPTS = \
	$(RUNTIME_PROFILING_VFLAGS) \
	$(RUNTIME_THREADS) \
	$(VMODE) \
	$(PLATFORM_OPTS) \
	$(TIMESCALE_OPTS) \
	$(PREPROC_DEFINES) \
	--top-module $(VLOG_MODEL) \
	$(sim_vsrcs_printf) \
	-f $(sim_common_files)

#----------------------------------------------------------------------------
# gcc configuration/optimization
#----------------------------------------------------------------------------
# -flto slows down compilation on small-memory and breaks on firesim-manager
CMODE := -O3 -fbranch-probabilities -march=native

VERILATOR_CXXFLAGS = \
	$(RUNTIME_PROFILING_CFLAGS) \
	$(TRACING_CFLAGS) \
	$(CMODE) \
	-std=c++11 \
	-D__STDC_FORMAT_MACROS \
	-DTEST_HARNESS=V$(VLOG_MODEL) \
	-DVERILATOR \
	-I$(RISCV)/include \
	-I$(dramsim_dir) \
	-I$(build_dir) \
	-include $(build_dir)/$(long_name).plusArgs \
	-include $(build_dir)/verilator.h \
	$(EXTRA_SIM_CXXFLAGS)

VERILATOR_LDFLAGS =	\
	$(RUNTIME_PROFILING_CFLAGS) \
	$(CMODE) \
	-L$(RISCV)/lib \
	-Wl,-rpath,$(RISCV)/lib \
	-L$(sim_dir) \
	-L$(dramsim_dir) \
	-lfesvr \
	-lpthread \
	-ldramsim \
	$(EXTRA_SIM_LDFLAGS)

VERILATOR_CC_OPTS = \
	-CFLAGS "$(VERILATOR_CXXFLAGS)" \
	-LDFLAGS "$(VERILATOR_LDFLAGS)"

#----------------------------------------------------------------------------
# full verilator+gcc opts
#----------------------------------------------------------------------------
VERILATOR_OPTS = $(VERILATOR_CC_OPTS) $(VERILATOR_NONCC_OPTS)

#############################################################################
# verilator build paths and file names
#############################################################################
model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

model_header = $(model_dir)/V$(VLOG_MODEL).h
model_header_debug = $(model_dir_debug)/V$(VLOG_MODEL).h

model_mk = $(model_dir)/V$(VLOG_MODEL).mk
model_mk_debug = $(model_dir_debug)/V$(VLOG_MODEL).mk

#############################################################################
# selectively enable verbose logging
#############################################################################
# see GNU Make static patterns
$(sim_vsrcs_printf) : %.printf.v : %.v
	$(base_dir)/bin/enable_printfs $< $@ "$(ENABLE_PRINTF_PATTERN)"

#############################################################################
# build makefile fragment that builds the verilator sim rules
#############################################################################
$(model_mk): $(sim_vsrcs_printf) $(sim_common_files) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir)
	mkdir -p $(model_dir)
	$(VERILATOR) $(VERILATOR_OPTS) $(EXTRA_SIM_SOURCES) -o $(sim) \
		-Mdir $(model_dir) -CFLAGS "-include $(model_header)"
	touch $@

$(model_mk_debug): $(sim_vsrcs_printf) $(sim_common_files) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir_debug)
	mkdir -p $(model_dir_debug)
	$(VERILATOR) $(VERILATOR_OPTS) $(EXTRA_SIM_SOURCES) -o $(sim_debug) \
		$(TRACING_OPTS) \
		-Mdir $(model_dir_debug) -CFLAGS "-include $(model_header_debug)"
	touch $@

#############################################################################
# invoke make to make verilator sim rules
#############################################################################
$(sim): $(model_mk) $(dramsim_lib)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir) -f V$(VLOG_MODEL).mk

$(sim_debug): $(model_mk_debug) $(dramsim_lib)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir_debug) -f V$(VLOG_MODEL).mk

#############################################################################
# create a verilator vpd rule
#############################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vcd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	(set -o pipefail && $(sim_debug) \
			$(SIM_FLAGS) \
			$(EXTRA_SIM_FLAGS) \
			$(VERBOSE_FLAGS) \
			-v$@.vcd $< \
			</dev/null \
			2> >(spike-dasm > $<.out) \
		| tee $<.log)

$(output_dir)/none.vpd: $(sim_debug)
	mkdir -p $(output_dir)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	(set -o pipefail && $(sim_debug) \
			$(SIM_FLAGS) \
			$(EXTRA_SIM_FLAGS) \
			$(VERBOSE_FLAGS) \
			-v$@.vcd none \
			</dev/null \
			2> >(spike-dasm > $(output_dir)/none.out) \
		| tee $(output_dir)/none.log)

#############################################################################
# general cleanup rule
#############################################################################
.PHONY: clean clean-sim clean-sim-debug
clean:
	rm -rf $(gen_dir) $(sim_prefix)-*

clean-sim:
	rm -rf $(model_dir) $(build_dir)/*.printf.v $(sim)

clean-sim-debug:
	rm -rf $(model_dir_debug) $(build_dir)/*.printf.v $(sim_debug)
