SHELL=/bin/bash
SED ?= sed

ifndef RISCV
$(error RISCV is unset. Did you source the Chipyard auto-generated env file (which activates the default conda environment)?)
else
$(info Running with RISCV=$(RISCV))
endif

#########################################################################################
# specify user-interface variables
#########################################################################################
HELP_COMPILATION_VARIABLES += \
"   EXTRA_GENERATOR_REQS   = additional make requirements needed for the main generator" \
"   EXTRA_SIM_CXXFLAGS     = additional CXXFLAGS for building simulators" \
"   EXTRA_SIM_LDFLAGS      = additional LDFLAGS for building simulators" \
"   EXTRA_SIM_SOURCES      = additional simulation sources needed for simulator" \
"   EXTRA_SIM_REQS         = additional make requirements to build the simulator" \
"   ENABLE_SBT_THIN_CLIENT = if set, use sbt's experimental thin client (works best when overridding SBT_BIN with the mainline sbt script)" \
"   EXTRA_CHISEL_OPTIONS   = additional options to pass to the Chisel compiler" \
"   EXTRA_FIRRTL_OPTIONS   = additional options to pass to the FIRRTL compiler"

EXTRA_GENERATOR_REQS ?= $(BOOTROM_TARGETS)
EXTRA_SIM_CXXFLAGS   ?=
EXTRA_SIM_LDFLAGS    ?=
EXTRA_SIM_SOURCES    ?=
EXTRA_SIM_REQS       ?=

#----------------------------------------------------------------------------
HELP_SIMULATION_VARIABLES += \
"   EXTRA_SIM_FLAGS        = additional runtime simulation flags (passed within +permissive)" \
"   NUMACTL                = set to '1' to wrap simulator in the appropriate numactl command" \
"   BREAK_SIM_PREREQ       = when running a binary, doesn't rebuild RTL on source changes"

EXTRA_SIM_FLAGS ?=
NUMACTL         ?= 0

NUMA_PREFIX = $(if $(filter $(NUMACTL),0),,$(shell $(base_dir)/scripts/numa_prefix))

#----------------------------------------------------------------------------
HELP_COMMANDS += \
"   run-binary                  = run [./$(shell basename $(sim))] and log instructions to file" \
"   run-binary-fast             = run [./$(shell basename $(sim))] and don't log instructions" \
"   run-binary-debug            = run [./$(shell basename $(sim_debug))] and log instructions and waveform to files" \
"   verilog                     = generate intermediate verilog files from chisel elaboration and firrtl passes" \
"   firrtl                      = generate intermediate firrtl files from chisel elaboration" \
"   run-tests                   = run all assembly and benchmark tests" \
"   launch-sbt                  = start sbt terminal" \
"   {shutdown,start}-sbt-server = shutdown or start sbt server if using ENABLE_SBT_THIN_CLIENT" \

#########################################################################################
# include additional subproject make fragments
# see HELP_COMPILATION_VARIABLES
#########################################################################################
include $(base_dir)/generators/cva6/cva6.mk
include $(base_dir)/generators/tracegen/tracegen.mk
include $(base_dir)/generators/nvdla/nvdla.mk
include $(base_dir)/tools/dromajo/dromajo.mk
include $(base_dir)/tools/torture.mk

#########################################################################################
# Prerequisite lists
#########################################################################################
# Returns a list of files in directory $1 with file extension $2.
# If available, use 'fd' to find the list of files, which is faster than 'find'.
ifeq ($(shell which fd 2> /dev/null),)
	lookup_srcs = $(shell find -L $(1)/ -name target -prune -o \( -iname "*.$(2)" ! -iname ".*" \) -print 2> /dev/null)
else
	lookup_srcs = $(shell fd -L -t f -e $(2) . $(1))
endif

SOURCE_DIRS = $(addprefix $(base_dir)/,generators sims/firesim/sim tools/barstools fpga/fpga-shells fpga/src)
SCALA_SOURCES = $(call lookup_srcs,$(SOURCE_DIRS),scala)
VLOG_SOURCES = $(call lookup_srcs,$(SOURCE_DIRS),sv) $(call lookup_srcs,$(SOURCE_DIRS),v)
# This assumes no SBT meta-build sources
SBT_SOURCE_DIRS = $(addprefix $(base_dir)/,generators sims/firesim/sim tools)
SBT_SOURCES = $(call lookup_srcs,$(SBT_SOURCE_DIRS),sbt) $(base_dir)/build.sbt $(base_dir)/project/plugins.sbt $(base_dir)/project/build.properties

#########################################################################################
# SBT Server Setup (start server / rebuild proj. defs. if SBT_SOURCES change)
#########################################################################################
$(SBT_THIN_CLIENT_TIMESTAMP): $(SBT_SOURCES)
ifneq (,$(wildcard $(SBT_THIN_CLIENT_TIMESTAMP)))
	cd $(base_dir) && $(SBT) "reload"
	touch $@
else
	cd $(base_dir) && $(SBT) "exit"
endif

#########################################################################################
# copy over bootrom files
#########################################################################################
$(build_dir) $(OUT_DIR):
	mkdir -p $@

$(BOOTROM_TARGETS): $(build_dir)/bootrom.%.img: $(TESTCHIP_RSRCS_DIR)/testchipip/bootrom/bootrom.%.img | $(build_dir)
	cp -f $< $@

#########################################################################################
# create firrtl file rule and variables
#########################################################################################
# AG: must re-elaborate if cva6 sources have changed... otherwise just run firrtl compile
$(FIRRTL_FILE) $(ANNO_FILE) &: $(SCALA_SOURCES) $(sim_files) $(SCALA_BUILDTOOL_DEPS) $(EXTRA_GENERATOR_REQS)
	mkdir -p $(build_dir)
	$(call run_scala_main,$(SBT_PROJECT),$(GENERATOR_PACKAGE).Generator,\
		--target-dir $(build_dir) \
		--name $(long_name) \
		--top-module $(MODEL_PACKAGE).$(MODEL) \
		--legacy-configs $(CONFIG_PACKAGE):$(CONFIG) \
		$(EXTRA_CHISEL_OPTIONS))

define firtool_extra_anno_contents
[
	{
		"class":"sifive.enterprise.firrtl.MarkDUTAnnotation",
		"target":"~$(MODEL)|$(TOP)"
	},
	{
		"class": "sifive.enterprise.firrtl.TestHarnessHierarchyAnnotation",
		"filename": "$(FIRTOOL_MODEL_HRCHY_JSON)"
	},
	{
		"class": "sifive.enterprise.firrtl.ModuleHierarchyAnnotation",
		"filename": "$(FIRTOOL_TOP_HRCHY_JSON)"
	}
]
endef
export firtool_extra_anno_contents
$(FINAL_ANNO_FILE) $(FIRTOOL_EXTRA_ANNO_FILE): $(ANNO_FILE)
	echo "$$firtool_extra_anno_contents" > $(FIRTOOL_EXTRA_ANNO_FILE)
	jq -s '[.[][]]' $(ANNO_FILE) $(FIRTOOL_EXTRA_ANNO_FILE) > $(FINAL_ANNO_FILE)

.PHONY: firrtl
firrtl: $(FIRRTL_FILE) $(FINAL_ANNO_FILE)

#########################################################################################
# create verilog files rules and variables
#########################################################################################
FIRTOOL_TARGETS = \
	$(FIRTOOL_SMEMS_CONF) \
	$(FIRTOOL_TOP_SMEMS_JSON) \
	$(FIRTOOL_TOP_HRCHY_JSON) \
	$(FIRTOOL_MODEL_HRCHY_JSON) \
	$(FIRTOOL_MODEL_SMEMS_JSON) \
	$(FIRTOOL_FILELIST) \
	$(FIRTOOL_BB_MODS_FILELIST)

# DOC include start: FirrtlCompiler
# hack: lower to middle firrtl if Fixed types are found
$(FIRTOOL_TARGETS) &: $(FIRRTL_FILE) $(FINAL_ANNO_FILE) $(VLOG_SOURCES)
	$(call run_scala_main,tapeout,barstools.tapeout.transforms.GenerateTop,\
		--no-dedup \
		--output-file $(SFC_FIRRTL_BASENAME) \
		--output-annotation-file $(SFC_ANNO_FILE) \
		--target-dir $(OUT_DIR) \
		--input-file $(FIRRTL_FILE) \
		--annotation-file $(FINAL_ANNO_FILE) \
		--log-level $(FIRRTL_LOGLEVEL) \
		--allow-unrecognized-annotations \
		-X $(if $(shell grep "Fixed<" $(FIRRTL_FILE)),middle,none) \
		$(EXTRA_FIRRTL_OPTIONS))
	$(if $(shell grep "Fixed<" $(FIRRTL_FILE)),mv $(SFC_FIRRTL_BASENAME).mid.fir $(SFC_FIRRTL_FILE),)
	firtool \
		--format=fir \
		-O=release \
		--dedup \
		--export-module-hierarchy \
		--emit-metadata \
		--verify-each=false \
		--disable-annotation-classless \
		--disable-annotation-unknown \
		--warn-on-unprocessed-annotations \
		--lowering-options=disallowPackedArrays,emittedLineLength=2048,noAlwaysComb,disallowLocalVariables,explicitBitcast,verifLabels,locationInfoStyle=wrapInAtSquareBracket \
		--repl-seq-mem \
		--repl-seq-mem-circuit=$(MODEL) \
		--repl-seq-mem-file=$(FIRTOOL_SMEMS_CONF) \
		--annotation-file=$(SFC_ANNO_FILE) \
		--split-verilog \
		-o $(OUT_DIR) \
		$(SFC_FIRRTL_FILE)
	$(SED) -i 's/.*/& /' $(FIRTOOL_SMEMS_CONF) # need trailing space for SFC macrocompiler
# DOC include end: FirrtlCompiler

$(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(ALL_MODS_FILELIST) $(BB_MODS_FILELIST) &: $(FIRTOOL_MODEL_HRCHY_JSON) $(FIRTOOL_FILELIST) $(FIRTOOL_BB_MODS_FILELIST)
	$(base_dir)/scripts/split-module-files.py \
		--model-hier-json $(FIRTOOL_MODEL_HRCHY_JSON) \
		--dut $(TOP) \
		--out-dut-filelist $(TOP_MODS_FILELIST) \
		--out-model-filelist $(MODEL_MODS_FILELIST) \
		--in-all-filelist $(FIRTOOL_FILELIST) \
		--target-dir $(OUT_DIR)
	$(SED) -e 's;^;$(OUT_DIR)/;' $(FIRTOOL_BB_MODS_FILELIST) > $(BB_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(TOP_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(MODEL_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(BB_MODS_FILELIST)
	sort -u $(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(BB_MODS_FILELIST) > $(ALL_MODS_FILELIST)

$(TOP_SMEMS_CONF) $(HARNESS_SMEMS_CONF) &: $(FIRTOOL_TOP_SMEMS_JSON) $(FIRTOOL_MODEL_SMEMS_JSON) $(FIRTOOL_SMEMS_CONF)
	$(base_dir)/scripts/split-mems-conf.py \
		--in-smems-conf $(FIRTOOL_SMEMS_CONF) \
		--in-dut-smems-json $(FIRTOOL_TOP_SMEMS_JSON) \
		--in-model-smems-json $(FIRTOOL_MODEL_SMEMS_JSON) \
		--out-dut-smems-conf $(TOP_SMEMS_CONF) \
		--out-model-smems-conf $(HARNESS_SMEMS_CONF)

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
MACROCOMPILER_MODE ?= --mode synflops
$(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR) &: $(TOP_SMEMS_CONF)
	$(call run_scala_main,tapeout,barstools.macros.MacroCompiler,-n $(TOP_SMEMS_CONF) -v $(TOP_SMEMS_FILE) -f $(TOP_SMEMS_FIR) $(MACROCOMPILER_MODE))

HARNESS_MACROCOMPILER_MODE = --mode synflops
$(HARNESS_SMEMS_FILE) $(HARNESS_SMEMS_FIR) &: $(HARNESS_SMEMS_CONF) | $(TOP_SMEMS_FILE)
	$(call run_scala_main,tapeout,barstools.macros.MacroCompiler, -n $(HARNESS_SMEMS_CONF) -v $(HARNESS_SMEMS_FILE) -f $(HARNESS_SMEMS_FIR) $(HARNESS_MACROCOMPILER_MODE))

########################################################################################
# remove duplicate files and headers in list of simulation file inputs
########################################################################################
$(sim_common_files): $(sim_files) $(ALL_MODS_FILELIST) $(TOP_SMEMS_FILE) $(HARNESS_SMEMS_FILE)
	sort -u $(sim_files) $(ALL_MODS_FILELIST) | grep -v '.*\.\(svh\|h\)$$' > $@
	echo "$(TOP_SMEMS_FILE)" >> $@
	echo "$(HARNESS_SMEMS_FILE)" >> $@

#########################################################################################
# helper rule to just make verilog files
#########################################################################################
.PHONY: verilog
verilog: $(sim_common_files)

#########################################################################################
# helper rules to run simulations
#########################################################################################
.PHONY: run-binary run-binary-fast run-binary-debug run-fast

check-binary:
ifeq (,$(BINARY))
	$(error BINARY variable is not set. Set it to the simulation binary)
endif

# allow you to override sim prereq
ifeq (,$(BREAK_SIM_PREREQ))
SIM_PREREQ = $(sim)
SIM_DEBUG_PREREQ = $(sim_debug)
endif

# run normal binary with hardware-logged insn dissassembly
run-binary: $(output_dir) $(SIM_PREREQ) check-binary
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $(BINARY) </dev/null 2> >(spike-dasm > $(sim_out_name).out) | tee $(sim_out_name).log)

# run simulator as fast as possible (no insn disassembly)
run-binary-fast: $(output_dir) $(SIM_PREREQ) check-binary
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(PERMISSIVE_OFF) $(BINARY) </dev/null | tee $(sim_out_name).log)

# run simulator with as much debug info as possible
run-binary-debug: $(output_dir) $(SIM_DEBUG_PREREQ) check-binary
	(set -o pipefail && $(NUMA_PREFIX) $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(WAVEFORM_FLAG) $(PERMISSIVE_OFF) $(BINARY) </dev/null 2> >(spike-dasm > $(sim_out_name).out) | tee $(sim_out_name).log)

run-fast: run-asm-tests-fast run-bmark-tests-fast

#########################################################################################
# helper rules to run simulator with fast loadmem via hex files
#########################################################################################
$(binary_hex): $(output_dir) $(BINARY)
	$(base_dir)/scripts/smartelf2hex.sh $(BINARY) > $(binary_hex)

run-binary-hex: check-binary
run-binary-hex: $(output_dir) $(SIM_PREREQ) $(binary_hex)
run-binary-hex: run-binary
run-binary-hex: override LOADMEM_ADDR = 80000000
run-binary-hex: override LOADMEM = $(binary_hex)
run-binary-hex: override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)
run-binary-debug-hex: check-binary
run-binary-debug-hex: $(output_dir) $(SIM_DEBUG_REREQ) $(binary_hex)
run-binary-debug-hex: run-binary-debug
run-binary-debug-hex: override LOADMEM_ADDR = 80000000
run-binary-debug-hex: override LOADMEM = $(binary_hex)
run-binary-debug-hex: override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)
run-binary-fast-hex: check-binary
run-binary-fast-hex: $(output_dir) $(SIM_PREREQ) $(binary_hex)
run-binary-fast-hex: run-binary-fast
run-binary-fast-hex: override LOADMEM_ADDR = 80000000
run-binary-fast-hex: override LOADMEM = $(binary_hex)
run-binary-fast-hex: override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)

#########################################################################################
# run assembly/benchmarks rules
#########################################################################################
$(output_dir):
	mkdir -p $@

$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/% $(output_dir)
	ln -sf $< $@

$(output_dir)/%.run: $(output_dir)/% $(SIM_PREREQ)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(PERMISSIVE_OFF) $< </dev/null | tee $<.log) && touch $@

$(output_dir)/%.out: $(output_dir)/% $(SIM_PREREQ)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $@) | tee $<.log)

#########################################################################################
# include build/project specific makefrags made from the generator
#########################################################################################
ifneq ($(filter run% %.run %.out %.vpd %.vcd %.fsdb,$(MAKECMDGOALS)),)
-include $(build_dir)/$(long_name).d
endif

#######################################
# Rules for building DRAMSim2 library
#######################################

dramsim_dir = $(base_dir)/tools/DRAMSim2
dramsim_lib = $(dramsim_dir)/libdramsim.a

$(dramsim_lib):
	$(MAKE) -C $(dramsim_dir) $(notdir $@)

################################################
# Helper to run SBT or manage the SBT server
################################################

SBT_COMMAND ?= shell
.PHONY: launch-sbt
launch-sbt:
	cd $(base_dir) && $(SBT_NON_THIN) "$(SBT_COMMAND)"

.PHONY: check-thin-client
check-thin-client:
ifeq (,$(ENABLE_SBT_THIN_CLIENT))
	$(error ENABLE_SBT_THIN_CLIENT not set.)
endif

.PHONY: shutdown-sbt-server
shutdown-sbt-server: check-thin-client
	cd $(base_dir) && $(SBT) "shutdown"

.PHONY: start-sbt-server
start-sbt-server: check-thin-client
	cd $(base_dir) && $(SBT) "exit"

#########################################################################################
# print help text
#########################################################################################
.PHONY: help
help:
	@for line in $(HELP_LINES); do echo "$$line"; done

#########################################################################################
# Implicit rule handling
#########################################################################################
# Disable all suffix rules to improve Make performance on systems running older
# versions of Make
.SUFFIXES:
