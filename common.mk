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
"   EXTRA_GENERATOR_REQS      = additional make requirements needed for the main generator" \
"   EXTRA_SIM_CXXFLAGS        = additional CXXFLAGS for building simulators" \
"   EXTRA_SIM_LDFLAGS         = additional LDFLAGS for building simulators" \
"   EXTRA_SIM_SOURCES         = additional simulation sources needed for simulator" \
"   EXTRA_SIM_REQS            = additional make requirements to build the simulator" \
"   ENABLE_CUSTOM_FIRRTL_PASS = if set, enable custom firrtl passes (SFC lowers to LowFIRRTL & MFC converts to Verilog)" \
"   ENABLE_YOSYS_FLOW         = if set, add compilation flags to enable the vlsi flow for yosys(tutorial flow)" \
"   EXTRA_CHISEL_OPTIONS      = additional options to pass to the Chisel compiler" \
"   EXTRA_BASE_FIRRTL_OPTIONS = additional options to pass to the Scala FIRRTL compiler" \
"   MFC_BASE_LOWERING_OPTIONS = override lowering options to pass to the MLIR FIRRTL compiler" \
"   ASPECTS                   = comma separated list of Chisel aspect flows to run (e.x. chipyard.upf.ChipTopUPFAspect)"

EXTRA_GENERATOR_REQS ?= $(BOOTROM_TARGETS)
EXTRA_SIM_CXXFLAGS   ?=
EXTRA_SIM_LDFLAGS    ?=
EXTRA_SIM_SOURCES    ?=
EXTRA_SIM_REQS       ?=
ENABLE_CUSTOM_FIRRTL_PASS += $(ENABLE_YOSYS_FLOW)

ifneq ($(ASPECTS), )
	comma = ,
	ASPECT_ARGS = $(foreach aspect, $(subst $(comma), , $(ASPECTS)), --with-aspect $(aspect))
endif

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
"   run-binaries                = run [./$(shell basename $(sim))] and log instructions to file" \
"   run-binaries-fast           = run [./$(shell basename $(sim))] and don't log instructions" \
"   run-binaries-debug          = run [./$(shell basename $(sim_debug))] and log instructions and waveform to files" \
"   verilog                     = generate intermediate verilog files from chisel elaboration and firrtl passes" \
"   firrtl                      = generate intermediate firrtl files from chisel elaboration" \
"   run-tests                   = run all assembly and benchmark tests" \
"   launch-sbt                  = start sbt terminal" \
"   find-config-fragments       = list all config. fragments"

#########################################################################################
# include additional subproject make fragments
# see HELP_COMPILATION_VARIABLES
#########################################################################################
include $(base_dir)/generators/cva6/cva6.mk
include $(base_dir)/generators/ibex/ibex.mk
include $(base_dir)/generators/tracegen/tracegen.mk
include $(base_dir)/generators/nvdla/nvdla.mk
include $(base_dir)/tools/torture.mk

#########################################################################################
# Prerequisite lists
#########################################################################################
# Returns a list of files in directories $1 with single file extension $2.
# If available, use 'fd' to find the list of files, which is faster than 'find'.
ifeq ($(shell which fd 2> /dev/null),)
	lookup_srcs = $(shell find -L $(1)/ -name target -prune -o \( -iname "*.$(2)" ! -iname ".*" \) -print 2> /dev/null)
else
	lookup_srcs = $(shell fd -L -t f -e $(2) . $(1))
endif

# Returns a list of files in directories $1 with *any* of the file extensions in $2
lookup_srcs_by_multiple_type = $(foreach type,$(2),$(call lookup_srcs,$(1),$(type)))

SCALA_EXT = scala
VLOG_EXT = sv v
CHIPYARD_SOURCE_DIRS = $(addprefix $(base_dir)/,generators sims/firesim/sim fpga/fpga-shells fpga/src)
CHIPYARD_SCALA_SOURCES = $(call lookup_srcs_by_multiple_type,$(CHIPYARD_SOURCE_DIRS),$(SCALA_EXT))
CHIPYARD_VLOG_SOURCES = $(call lookup_srcs_by_multiple_type,$(CHIPYARD_SOURCE_DIRS),$(VLOG_EXT))
BARSTOOLS_SOURCE_DIRS = $(addprefix $(base_dir)/,tools/barstools)
BARSTOOLS_SCALA_SOURCES = $(call lookup_srcs_by_multiple_type,$(BARSTOOLS_SOURCE_DIRS),$(SCALA_EXT))
BARSTOOLS_VLOG_SOURCES = $(call lookup_srcs_by_multiple_type,$(BARSTOOLS_SOURCE_DIRS),$(VLOG_EXT))
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
$(build_dir):
	mkdir -p $@

$(BOOTROM_TARGETS): $(build_dir)/bootrom.%.img: $(TESTCHIP_RSRCS_DIR)/testchipip/bootrom/bootrom.%.img | $(build_dir)
	cp -f $< $@

#########################################################################################
# compile scala jars
#########################################################################################
$(CHIPYARD_CLASSPATH_TARGETS) &: $(CHIPYARD_SCALA_SOURCES) $(SCALA_BUILDTOOL_DEPS)
	mkdir -p $(dir $@)
	$(call run_sbt_assembly,$(SBT_PROJECT),$(CHIPYARD_CLASSPATH))

# order only dependency between sbt runs needed to avoid concurrent sbt runs
$(TAPEOUT_CLASSPATH_TARGETS) &: $(BARSTOOLS_SCALA_SOURCES) $(SCALA_BUILDTOOL_DEPS) | $(CHIPYARD_CLASSPATH_TARGETS)
	mkdir -p $(dir $@)
	$(call run_sbt_assembly,tapeout,$(TAPEOUT_CLASSPATH))

#########################################################################################
# verilog generation pipeline
#########################################################################################
# AG: must re-elaborate if cva6 sources have changed... otherwise just run firrtl compile
$(FIRRTL_FILE) $(ANNO_FILE) $(CHISEL_LOG_FILE) &: $(CHIPYARD_CLASSPATH_TARGETS) $(EXTRA_GENERATOR_REQS)
	mkdir -p $(build_dir)
	(set -o pipefail && $(call run_jar_scala_main,$(CHIPYARD_CLASSPATH),$(GENERATOR_PACKAGE).Generator,\
		--target-dir $(build_dir) \
		--name $(long_name) \
		--top-module $(MODEL_PACKAGE).$(MODEL) \
		--legacy-configs $(CONFIG_PACKAGE):$(CONFIG) \
		$(ASPECT_ARGS) \
		$(EXTRA_CHISEL_OPTIONS)) | tee $(CHISEL_LOG_FILE))

define mfc_extra_anno_contents
[
	{
		"class":"sifive.enterprise.firrtl.MarkDUTAnnotation",
		"target":"~$(MODEL)|$(TOP)"
	},
	{
		"class": "sifive.enterprise.firrtl.TestHarnessHierarchyAnnotation",
		"filename": "$(MFC_MODEL_HRCHY_JSON)"
	},
	{
		"class": "sifive.enterprise.firrtl.ModuleHierarchyAnnotation",
		"filename": "$(MFC_TOP_HRCHY_JSON)"
	}
]
endef
define sfc_extra_low_transforms_anno_contents
[
	{
		"class": "firrtl.stage.RunFirrtlTransformAnnotation",
		"transform": "barstools.tapeout.transforms.ExtraLowTransforms"
	}
]
endef
export mfc_extra_anno_contents
export sfc_extra_low_transforms_anno_contents
$(EXTRA_ANNO_FILE) $(MFC_EXTRA_ANNO_FILE) $(SFC_EXTRA_ANNO_FILE) &: $(ANNO_FILE)
	echo "$$mfc_extra_anno_contents" > $(MFC_EXTRA_ANNO_FILE)
	echo "$$sfc_extra_low_transforms_anno_contents" > $(SFC_EXTRA_ANNO_FILE)
	jq -s '[.[][]]' $(ANNO_FILE) $(MFC_EXTRA_ANNO_FILE) > $(EXTRA_ANNO_FILE)

.PHONY: firrtl
firrtl: $(FIRRTL_FILE) $(FINAL_ANNO_FILE)

#########################################################################################
# create verilog files rules and variables
#########################################################################################
SFC_MFC_TARGETS = \
	$(MFC_SMEMS_CONF) \
	$(MFC_TOP_SMEMS_JSON) \
	$(MFC_TOP_HRCHY_JSON) \
	$(MFC_MODEL_HRCHY_JSON) \
	$(MFC_MODEL_SMEMS_JSON) \
	$(MFC_FILELIST) \
	$(MFC_BB_MODS_FILELIST) \
	$(GEN_COLLATERAL_DIR)

SFC_REPL_SEQ_MEM = --infer-rw --repl-seq-mem -c:$(MODEL):-o:$(SFC_SMEMS_CONF)
MFC_BASE_LOWERING_OPTIONS ?= emittedLineLength=2048,noAlwaysComb,disallowLocalVariables,verifLabels,locationInfoStyle=wrapInAtSquareBracket

# DOC include start: FirrtlCompiler
# There are two possible cases for this step. In the first case, SFC
# compiles Chisel to CHIRRTL, and MFC compiles CHIRRTL to Verilog. Otherwise,
# when custom FIRRTL transforms are included or if a Fixed type is used within
# the dut, SFC compiles Chisel to LowFIRRTL and MFC compiles it to Verilog.
# Users can indicate to the Makefile of custom FIRRTL transforms by setting the
# "ENABLE_CUSTOM_FIRRTL_PASS" variable.
#
# hack: lower to low firrtl if Fixed types are found
# hack: when using dontTouch, io.cpu annotations are not removed by SFC,
# hence we remove them manually by using jq before passing them to firtool

$(SFC_LEVEL) $(EXTRA_FIRRTL_OPTIONS) &: $(FIRRTL_FILE)
ifeq (,$(ENABLE_CUSTOM_FIRRTL_PASS))
	echo $(if $(shell grep "Fixed<" $(FIRRTL_FILE)), low, none) > $(SFC_LEVEL)
	echo "$(EXTRA_BASE_FIRRTL_OPTIONS)" $(if $(shell grep "Fixed<" $(FIRRTL_FILE)), "$(SFC_REPL_SEQ_MEM)",) > $(EXTRA_FIRRTL_OPTIONS)
else
	echo low > $(SFC_LEVEL)
	echo "$(EXTRA_BASE_FIRRTL_OPTIONS)" "$(SFC_REPL_SEQ_MEM)" > $(EXTRA_FIRRTL_OPTIONS)
endif

$(MFC_LOWERING_OPTIONS):
	mkdir -p $(dir $@)
ifeq (,$(ENABLE_YOSYS_FLOW))
	echo "$(MFC_BASE_LOWERING_OPTIONS)" > $@
else
	echo "$(MFC_BASE_LOWERING_OPTIONS),disallowPackedArrays" > $@
endif

$(FINAL_ANNO_FILE): $(EXTRA_ANNO_FILE) $(SFC_EXTRA_ANNO_FILE) $(SFC_LEVEL)
	if [ $(shell cat $(SFC_LEVEL)) = low ]; then jq -s '[.[][]]' $(EXTRA_ANNO_FILE) $(SFC_EXTRA_ANNO_FILE) > $@; fi
	if [ $(shell cat $(SFC_LEVEL)) = none ]; then cat $(EXTRA_ANNO_FILE) > $@; fi
	touch $@

$(SFC_MFC_TARGETS) &: private TMP_DIR := $(shell mktemp -d -t cy-XXXXXXXX)
$(SFC_MFC_TARGETS) &: $(TAPEOUT_CLASSPATH_TARGETS) $(FIRRTL_FILE) $(FINAL_ANNO_FILE) $(SFC_LEVEL) $(EXTRA_FIRRTL_OPTIONS) $(MFC_LOWERING_OPTIONS) $(CHIPYARD_VLOG_SOURCES) $(BARSTOOLS_VLOG_SOURCES)
	rm -rf $(GEN_COLLATERAL_DIR)
	$(call run_jar_scala_main,$(TAPEOUT_CLASSPATH),barstools.tapeout.transforms.GenerateModelStageMain,\
		--no-dedup \
		--output-file $(SFC_FIRRTL_BASENAME) \
		--output-annotation-file $(SFC_ANNO_FILE) \
		--target-dir $(GEN_COLLATERAL_DIR) \
		--input-file $(FIRRTL_FILE) \
		--annotation-file $(FINAL_ANNO_FILE) \
		--log-level $(FIRRTL_LOGLEVEL) \
		--allow-unrecognized-annotations \
		-X $(shell cat $(SFC_LEVEL)) \
		$(shell cat $(EXTRA_FIRRTL_OPTIONS)))
	-mv $(SFC_FIRRTL_BASENAME).lo.fir $(SFC_FIRRTL_FILE) 2> /dev/null # Optionally change file type when SFC generates LowFIRRTL
	@if [ $(shell cat $(SFC_LEVEL)) = low ]; then cat $(SFC_ANNO_FILE) | jq 'del(.[] | select(.target | test("io.cpu"))?)' > $(TMP_DIR)/unnec-anno-deleted.sfc.anno.json; fi
	@if [ $(shell cat $(SFC_LEVEL)) = low ]; then cat $(TMP_DIR)/unnec-anno-deleted.sfc.anno.json | jq 'del(.[] | select(.class | test("SRAMAnnotation"))?)' > $(TMP_DIR)/unnec-anno-deleted2.sfc.anno.json; fi
	@if [ $(shell cat $(SFC_LEVEL)) = low ]; then cat $(TMP_DIR)/unnec-anno-deleted2.sfc.anno.json > $(SFC_ANNO_FILE) && rm $(TMP_DIR)/unnec-anno-deleted.sfc.anno.json && rm $(TMP_DIR)/unnec-anno-deleted2.sfc.anno.json; fi
	firtool \
		--format=fir \
		--dedup \
		--export-module-hierarchy \
		--emit-metadata \
		--verify-each=true \
		--warn-on-unprocessed-annotations \
		--disable-annotation-classless \
		--disable-annotation-unknown \
		--mlir-timing \
		--lowering-options=$(shell cat $(MFC_LOWERING_OPTIONS)) \
		--repl-seq-mem \
		--repl-seq-mem-file=$(MFC_SMEMS_CONF) \
		--repl-seq-mem-circuit=$(MODEL) \
		--annotation-file=$(SFC_ANNO_FILE) \
		--split-verilog \
		-o $(GEN_COLLATERAL_DIR) \
		$(SFC_FIRRTL_FILE)
	-mv $(SFC_SMEMS_CONF) $(MFC_SMEMS_CONF) 2> /dev/null
	$(SED) -i 's/.*/& /' $(MFC_SMEMS_CONF) # need trailing space for SFC macrocompiler
# DOC include end: FirrtlCompiler

$(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(ALL_MODS_FILELIST) $(BB_MODS_FILELIST) $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED) &: $(MFC_MODEL_HRCHY_JSON) $(MFC_TOP_HRCHY_JSON) $(MFC_FILELIST) $(MFC_BB_MODS_FILELIST)
	$(base_dir)/scripts/uniquify-module-names.py \
		--model-hier-json $(MFC_MODEL_HRCHY_JSON) \
		--top-hier-json $(MFC_TOP_HRCHY_JSON) \
		--in-all-filelist $(MFC_FILELIST) \
		--dut $(TOP) \
		--model $(MODEL) \
		--target-dir $(GEN_COLLATERAL_DIR) \
		--out-dut-filelist $(TOP_MODS_FILELIST) \
		--out-model-filelist $(MODEL_MODS_FILELIST) \
		--out-model-hier-json $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED) \
		--gcpath $(GEN_COLLATERAL_DIR)
	$(SED) -e 's;^;$(GEN_COLLATERAL_DIR)/;' $(MFC_BB_MODS_FILELIST) > $(BB_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(TOP_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(MODEL_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(BB_MODS_FILELIST)
	sort -u $(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(BB_MODS_FILELIST) > $(ALL_MODS_FILELIST)

$(TOP_SMEMS_CONF) $(MODEL_SMEMS_CONF) &:  $(MFC_SMEMS_CONF) $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED)
	$(base_dir)/scripts/split-mems-conf.py \
		--in-smems-conf $(MFC_SMEMS_CONF) \
		--in-model-hrchy-json $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED) \
		--dut-module-name $(TOP) \
		--model-module-name $(MODEL) \
		--out-dut-smems-conf $(TOP_SMEMS_CONF) \
		--out-model-smems-conf $(MODEL_SMEMS_CONF)

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
TOP_MACROCOMPILER_MODE ?= --mode synflops
$(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR) &: $(TAPEOUT_CLASSPATH_TARGETS) $(TOP_SMEMS_CONF)
	$(call run_jar_scala_main,$(TAPEOUT_CLASSPATH),barstools.macros.MacroCompiler,-n $(TOP_SMEMS_CONF) -v $(TOP_SMEMS_FILE) -f $(TOP_SMEMS_FIR) $(TOP_MACROCOMPILER_MODE))

MODEL_MACROCOMPILER_MODE = --mode synflops
$(MODEL_SMEMS_FILE) $(MODEL_SMEMS_FIR) &: $(TAPEOUT_CLASSPATH_TARGETS) $(MODEL_SMEMS_CONF) | $(TOP_SMEMS_FILE)
	$(call run_jar_scala_main,$(TAPEOUT_CLASSPATH),barstools.macros.MacroCompiler, -n $(MODEL_SMEMS_CONF) -v $(MODEL_SMEMS_FILE) -f $(MODEL_SMEMS_FIR) $(MODEL_MACROCOMPILER_MODE))

########################################################################################
# remove duplicate files and headers in list of simulation file inputs
# note: {MODEL,TOP}_BB_MODS_FILELIST is added as a req. so that the files get generated,
#       however it is really unneeded since ALL_MODS_FILELIST includes all BB files
########################################################################################
$(sim_common_files): $(sim_files) $(ALL_MODS_FILELIST) $(TOP_SMEMS_FILE) $(MODEL_SMEMS_FILE) $(BB_MODS_FILELIST)
	sort -u $(sim_files) $(ALL_MODS_FILELIST) | grep -v '.*\.\(svh\|h\)$$' > $@
	echo "$(TOP_SMEMS_FILE)" >> $@
	echo "$(MODEL_SMEMS_FILE)" >> $@

#########################################################################################
# helper rule to just make verilog files
#########################################################################################
.PHONY: verilog
verilog: $(sim_common_files)

#########################################################################################
# helper rules to run simulations
#########################################################################################
.PHONY: run-binary run-binary-fast run-binary-debug run-fast
	%.check-exists check-binary check-binaries

check-binary:
ifeq (,$(BINARY))
	$(error BINARY variable is not set. Set it to the simulation binary)
endif

check-binaries:
ifeq (,$(BINARIES))
	$(error BINARIES variable is not set. Set it to the list of simulation binaries to run)
endif

%.check-exists:
	if [ "$*" != "none" ] && [ ! -f "$*" ]; then printf "\n\nBinary $* not found\n\n"; exit 1; fi

# allow you to override sim prereq
ifeq (,$(BREAK_SIM_PREREQ))
SIM_PREREQ = $(sim)
SIM_DEBUG_PREREQ = $(sim_debug)
endif

# Function to generate the loadmem flag. First arg is the binary
ifeq ($(LOADMEM),1)
# If LOADMEM=1, assume BINARY is the loadmem elf
get_loadmem_flag = +loadmem=$(1)
else ifneq ($(LOADMEM),)
# Otherwise, assume the variable points to an elf file
get_loadmem_flag = +loadmem=$(LOADMEM)
endif

ifneq ($(LOADARCH),)
get_loadarch_flag = +loadarch=$(subst mem.elf,loadarch,$(1))
endif

# get the output path base name for simulation outputs, First arg is the binary
get_sim_out_name = $(output_dir)/$(call get_out_name,$(1))
# sim flags that are common to run-binary/run-binary-fast/run-binary-debug
get_common_sim_flags = $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(call get_loadmem_flag,$(1)) $(call get_loadarch_flag,$(1))

.PHONY: %.run %.run.debug %.run.fast

# run normal binary with hardware-logged insn dissassembly
run-binary: check-binary $(BINARY).run
run-binaries: check-binaries $(addsuffix .run,$(BINARIES))

%.run: %.check-exists $(SIM_PREREQ) | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(call get_common_sim_flags,$*) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $* </dev/null 2> >(spike-dasm > $(call get_sim_out_name,$*).out) | tee $(call get_sim_out_name,$*).log)

# run simulator as fast as possible (no insn disassembly)
run-binary-fast: check-binary $(BINARY).run.fast
run-binaries-fast: check-binaries $(addsuffix .run.fast,$(BINARIES))

%.run.fast: %.check-exists $(SIM_PREREQ) | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(call get_common_sim_flags,$*) $(PERMISSIVE_OFF) $* </dev/null | tee $(call get_sim_out_name,$*).log)

# run simulator with as much debug info as possible
run-binary-debug: check-binary $(BINARY).run.debug
run-binaries-debug: check-binaries $(addsuffix .run.debug,$(BINARIES))

%.run.debug: %.check-exists $(SIM_DEBUG_PREREQ) | $(output_dir)
	if [ "$*" != "none" ]; then riscv64-unknown-elf-objdump -D -S $* > $(call get_sim_out_name,$*).dump ; fi
	(set -o pipefail && $(NUMA_PREFIX) $(sim_debug) $(PERMISSIVE_ON) $(call get_common_sim_flags,$*) $(VERBOSE_FLAGS) $(call get_waveform_flag,$(call get_sim_out_name,$*)) $(PERMISSIVE_OFF) $* </dev/null 2> >(spike-dasm > $(call get_sim_out_name,$*).out) | tee $(call get_sim_out_name,$*).log)

run-fast: run-asm-tests-fast run-bmark-tests-fast

#########################################################################################
# helper rules to run simulator with fast loadmem
# LEGACY - use LOADMEM=1 instead
#########################################################################################
run-binary-hex: $(BINARY).run
run-binary-hex: override SIM_FLAGS += +loadmem=$(BINARY)
run-binary-debug-hex: $(BINARY).run.debug
run-binary-debug-hex: override SIM_FLAGS += +loadmem=$(BINARY)
run-binary-fast-hex: $(BINARY).run.fast
run-binary-fast-hex: override SIM_FLAGS += +loadmem=$(BINARY)

#########################################################################################
# run assembly/benchmarks rules
#########################################################################################
$(output_dir):
	mkdir -p $@

$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/% | $(output_dir)
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
# Helper to run SBT
################################################
SBT_COMMAND ?= shell
.PHONY: launch-sbt
launch-sbt:
	cd $(base_dir) && $(SBT) "$(SBT_COMMAND)"

#########################################################################################
# print help text (and other help)
#########################################################################################
# helper to add newlines (avoid bash argument too long)
define \n


endef

.PHONY: find-config-fragments
find-config-fragments:
	$(call run_scala_main,chipyard,chipyard.ConfigFinder,)

.PHONY: help
help:
	@for line in $(HELP_LINES); do echo "$$line"; done

#########################################################################################
# Implicit rule handling
#########################################################################################
# Disable all suffix rules to improve Make performance on systems running older
# versions of Make
.SUFFIXES:
