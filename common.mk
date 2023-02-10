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
"   ENABLE_SBT_THIN_CLIENT    = if set, use sbt's experimental thin client (works best when overridding SBT_BIN with the mainline sbt script)" \
"   ENABLE_CUSTOM_FIRRTL_PASS = if set, enable custom firrtl passes (SFC lowers to LowFIRRTL & MFC converts to Verilog) \
"   EXTRA_CHISEL_OPTIONS      = additional options to pass to the Chisel compiler" \
"   EXTRA_FIRRTL_OPTIONS      = additional options to pass to the FIRRTL compiler"

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
export mfc_extra_anno_contents
$(FINAL_ANNO_FILE) $(MFC_EXTRA_ANNO_FILE): $(ANNO_FILE)
	echo "$$mfc_extra_anno_contents" > $(MFC_EXTRA_ANNO_FILE)
	jq -s '[.[][]]' $(ANNO_FILE) $(MFC_EXTRA_ANNO_FILE) > $(FINAL_ANNO_FILE)

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
	$(MFC_BB_MODS_FILELIST)

SFC_REPL_SEQ_MEM = --infer-rw --repl-seq-mem -c:$(MODEL):-o:$(SFC_SMEMS_CONF)


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
$(SFC_MFC_TARGETS) &: $(FIRRTL_FILE) $(FINAL_ANNO_FILE) $(VLOG_SOURCES)
ifeq (,$(ENABLE_CUSTOM_FIRRTL_PASS))
	$(eval SFC_LEVEL := $(if $(shell grep "Fixed<" $(FIRRTL_FILE)), low, none))
	$(eval EXTRA_FIRRTL_OPTIONS += $(if $(shell grep "Fixed<" $(FIRRTL_FILE)), $(SFC_REPL_SEQ_MEM),))
else
	$(eval SFC_LEVEL := low)
	$(eval EXTRA_FIRRTL_OPTIONS += $(SFC_REPL_SEQ_MEM))
endif
	$(call run_scala_main,tapeout,barstools.tapeout.transforms.GenerateModelStageMain,\
		--no-dedup \
		--output-file $(SFC_FIRRTL_BASENAME) \
		--output-annotation-file $(SFC_ANNO_FILE) \
		--target-dir $(OUT_DIR) \
		--input-file $(FIRRTL_FILE) \
		--annotation-file $(FINAL_ANNO_FILE) \
		--log-level $(FIRRTL_LOGLEVEL) \
		--allow-unrecognized-annotations \
		-DX $(SFC_LEVEL) \
		-X $(SFC_LEVEL) \
		$(EXTRA_FIRRTL_OPTIONS)) # -X and -DX are duplicates to allow for extra FIRRTL passes to be run
	-mv $(SFC_FIRRTL_BASENAME).lo.fir $(SFC_FIRRTL_FILE) # Optionally change file type when SFC generates LowFIRRTL
	@if [ "$(SFC_LEVEL)" = low ]; then cat $(SFC_ANNO_FILE) | jq 'del(.[] | select(.target | test("io.cpu"))?)' > /tmp/unnec-anno-deleted.sfc.anno.json; fi
	@if [ "$(SFC_LEVEL)" = low ]; then cat /tmp/unnec-anno-deleted.sfc.anno.json | jq 'del(.[] | select(.class | test("SRAMAnnotation"))?)' > /tmp/unnec-anno-deleted2.sfc.anno.json; fi
	@if [ "$(SFC_LEVEL)" = low ]; then cat /tmp/unnec-anno-deleted2.sfc.anno.json > $(SFC_ANNO_FILE) && rm /tmp/unnec-anno-deleted.sfc.anno.json && rm /tmp/unnec-anno-deleted2.sfc.anno.json; fi
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
		--lowering-options=emittedLineLength=2048,noAlwaysComb,disallowPackedArrays,disallowLocalVariables,verifLabels,locationInfoStyle=wrapInAtSquareBracket \
		--repl-seq-mem \
		--repl-seq-mem-file=$(MFC_SMEMS_CONF) \
		--repl-seq-mem-circuit=$(MODEL) \
		--annotation-file=$(SFC_ANNO_FILE) \
		--split-verilog \
		-o $(OUT_DIR) \
		$(SFC_FIRRTL_FILE)
	-mv $(SFC_SMEMS_CONF) $(MFC_SMEMS_CONF)
	$(SED) -i 's/.*/& /' $(MFC_SMEMS_CONF) # need trailing space for SFC macrocompiler
# DOC include end: FirrtlCompiler

$(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(ALL_MODS_FILELIST) $(BB_MODS_FILELIST) &: $(MFC_MODEL_HRCHY_JSON) $(MFC_FILELIST) $(MFC_BB_MODS_FILELIST)
	$(base_dir)/scripts/split-module-files.py \
		--model-hier-json $(MFC_MODEL_HRCHY_JSON) \
		--dut $(TOP) \
		--out-dut-filelist $(TOP_MODS_FILELIST) \
		--out-model-filelist $(MODEL_MODS_FILELIST) \
		--in-all-filelist $(MFC_FILELIST) \
		--target-dir $(OUT_DIR)
	$(SED) -e 's;^;$(OUT_DIR)/;' $(MFC_BB_MODS_FILELIST) > $(BB_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(TOP_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(MODEL_MODS_FILELIST)
	$(SED) -i 's/\.\///' $(BB_MODS_FILELIST)
	sort -u $(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(BB_MODS_FILELIST) > $(ALL_MODS_FILELIST)

$(TOP_BB_MODS_FILELIST) $(MODEL_BB_MODS_FILELIST) &: $(BB_MODS_FILELIST) $(MFC_TOP_HRCHY_JSON) $(FINAL_ANNO_FILE)
	$(base_dir)/scripts/split-bb-files.py \
		--in-bb-f $(BB_MODS_FILELIST) \
		--in-top-hrchy-json $(MFC_TOP_HRCHY_JSON) \
		--in-anno-json $(FINAL_ANNO_FILE) \
		--out-top-bb-f $(TOP_BB_MODS_FILELIST) \
		--out-model-bb-f $(MODEL_BB_MODS_FILELIST)

$(TOP_SMEMS_CONF) $(MODEL_SMEMS_CONF) &:  $(MFC_SMEMS_CONF) $(MFC_MODEL_HRCHY_JSON)
	$(base_dir)/scripts/split-mems-conf.py \
		--in-smems-conf $(MFC_SMEMS_CONF) \
		--in-model-hrchy-json $(MFC_MODEL_HRCHY_JSON) \
		--dut-module-name $(TOP) \
		--model-module-name $(MODEL) \
		--out-dut-smems-conf $(TOP_SMEMS_CONF) \
		--out-model-smems-conf $(MODEL_SMEMS_CONF)

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
TOP_MACROCOMPILER_MODE ?= --mode synflops
$(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR) &: $(TOP_SMEMS_CONF)
	$(call run_scala_main,tapeout,barstools.macros.MacroCompiler,-n $(TOP_SMEMS_CONF) -v $(TOP_SMEMS_FILE) -f $(TOP_SMEMS_FIR) $(TOP_MACROCOMPILER_MODE))

MODEL_MACROCOMPILER_MODE = --mode synflops
$(MODEL_SMEMS_FILE) $(MODEL_SMEMS_FIR) &: $(MODEL_SMEMS_CONF) | $(TOP_SMEMS_FILE)
	$(call run_scala_main,tapeout,barstools.macros.MacroCompiler, -n $(MODEL_SMEMS_CONF) -v $(MODEL_SMEMS_FILE) -f $(MODEL_SMEMS_FIR) $(MODEL_MACROCOMPILER_MODE))

########################################################################################
# remove duplicate files and headers in list of simulation file inputs
# note: {MODEL,TOP}_BB_MODS_FILELIST is added as a req. so that the files get generated,
#       however it is really unneeded since ALL_MODS_FILELIST includes all BB files
########################################################################################
$(sim_common_files): $(sim_files) $(ALL_MODS_FILELIST) $(TOP_SMEMS_FILE) $(MODEL_SMEMS_FILE) $(TOP_BB_MODS_FILELIST) $(MODEL_BB_MODS_FILELIST)
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
run-binary: $(SIM_PREREQ) check-binary | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $(BINARY) </dev/null 2> >(spike-dasm > $(sim_out_name).out) | tee $(sim_out_name).log)

# run simulator as fast as possible (no insn disassembly)
run-binary-fast: $(SIM_PREREQ) check-binary | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(PERMISSIVE_OFF) $(BINARY) </dev/null | tee $(sim_out_name).log)

# run simulator with as much debug info as possible
run-binary-debug: $(SIM_DEBUG_PREREQ) check-binary | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(VERBOSE_FLAGS) $(WAVEFORM_FLAG) $(PERMISSIVE_OFF) $(BINARY) </dev/null 2> >(spike-dasm > $(sim_out_name).out) | tee $(sim_out_name).log)

run-fast: run-asm-tests-fast run-bmark-tests-fast

#########################################################################################
# helper rules to run simulator with fast loadmem via hex files
#########################################################################################
$(binary_hex): $(firstword $(BINARY)) | $(output_dir)
	$(base_dir)/scripts/smartelf2hex.sh $(firstword $(BINARY)) > $(binary_hex)

run-binary-hex: check-binary
run-binary-hex: $(SIM_PREREQ) $(binary_hex) | $(output_dir)
run-binary-hex: run-binary
run-binary-hex: override LOADMEM_ADDR = 80000000
run-binary-hex: override LOADMEM = $(binary_hex)
run-binary-hex: override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)
run-binary-debug-hex: check-binary
run-binary-debug-hex: $(SIM_DEBUG_REREQ) $(binary_hex) | $(output_dir)
run-binary-debug-hex: run-binary-debug
run-binary-debug-hex: override LOADMEM_ADDR = 80000000
run-binary-debug-hex: override LOADMEM = $(binary_hex)
run-binary-debug-hex: override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)
run-binary-fast-hex: check-binary
run-binary-fast-hex: $(SIM_PREREQ) $(binary_hex) | $(output_dir)
run-binary-fast-hex: run-binary-fast
run-binary-fast-hex: override LOADMEM_ADDR = 80000000
run-binary-fast-hex: override LOADMEM = $(binary_hex)
run-binary-fast-hex: override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)

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
