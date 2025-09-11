
# Note: Individual rules that use RISCV or external tools perform local checks to avoid
# blocking unrelated targets. Use $(require_riscv) and $(call require_cmd,<tool>) inside recipes.


define require_riscv
	@if [ -z "$(RISCV)" ]; then \
	  echo "RISCV is unset. Source env.sh (which activates the default conda env) before building sims." 1>&2; \
	  exit 1; \
	fi
endef

# Verify a tool is present in PATH; usage: $(call require_cmd,verilator)
define require_cmd
	@command -v $(1) >/dev/null 2>&1 \
		|| { echo "Error: $(1) not found in PATH. Set up your tool environment before building this target." >&2; exit 1; }
endef

# Require minimum firtool version when building with Chisel 7
define require_firtool_version
	@if [ -n "$(USE_CHISEL7)" ]; then \
	  vline=`$(FIRTOOL_BIN) --version 2>/dev/null | grep -E 'CIRCT firtool-[0-9]+\.[0-9]+\.[0-9]+' | head -1`; \
	  vstr=$${vline##*firtool-}; \
	  if [ -z "$$vstr" ]; then \
	    echo "Error: Unable to parse firtool version. Ensure '$(FIRTOOL_BIN) --version' prints 'CIRCT firtool-X.Y.Z'." >&2; exit 1; \
	  fi; \
	  maj=$${vstr%%.*}; rest=$${vstr#*.}; min=$${rest%%.*}; pat=$${rest#*.}; \
	  if [ "$$maj" -lt 1 ] || { [ "$$maj" -eq 1 ] && [ "$$min" -lt 129 ]; }; then \
	    echo "Error: USE_CHISEL7 requires firtool >= 1.129.0, found $$vstr. Please update CIRCT firtool." >&2; exit 1; \
	  fi; \
	fi
endef

#########################################################################################
# specify user-interface variables
#########################################################################################
HELP_COMPILATION_VARIABLES += \
"   EXTRA_GENERATOR_REQS      = additional make requirements needed for the main generator" \
"   EXTRA_SIM_CXXFLAGS        = additional CXXFLAGS for building simulators" \
"   EXTRA_SIM_LDFLAGS         = additional LDFLAGS for building simulators" \
"   EXTRA_SIM_SOURCES         = additional simulation sources needed for simulator" \
"   EXTRA_SIM_REQS            = additional make requirements to build the simulator" \
"   EXTRA_SIM_OUT_NAME        = additional suffix appended to the simulation .out log filename" \
"   EXTRA_SIM_PREPROC_DEFINES = additional Verilog preprocessor defines passed to the simulator" \
"   ENABLE_YOSYS_FLOW         = if set, add compilation flags to enable the vlsi flow for yosys(tutorial flow)" \
"   EXTRA_CHISEL_OPTIONS      = additional options to pass to the Chisel compiler" \
"   MFC_BASE_LOWERING_OPTIONS = override lowering options to pass to the MLIR FIRRTL compiler" \
"   ASPECTS                   = comma separated list of Chisel aspect flows to run (e.x. chipyard.upf.ChipTopUPFAspect)"

EXTRA_GENERATOR_REQS ?=
EXTRA_SIM_CXXFLAGS   ?=
EXTRA_SIM_LDFLAGS    ?=
EXTRA_SIM_SOURCES    ?=
EXTRA_SIM_REQS       ?=
EXTRA_SIM_OUT_NAME   ?=

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
"   find-configs                = list Chipyard Config classes (eligible CONFIG=)" \
"   find-config-fragments       = list all config. fragments" \
"   run-firtool                 = run CIRCT firtool to emit Verilog/JSON/mem conf" \
"   run-uniquify                = run uniquify-module-names on current elaboration outputs"

#########################################################################################
# include additional subproject make fragments
# see HELP_COMPILATION_VARIABLES
#########################################################################################
include $(base_dir)/generators/tracegen/tracegen.mk
include $(base_dir)/tools/torture.mk
# Optional generator make fragments should not fail build if absent
# Wildcard include for standardized per-generator make fragments
-include $(wildcard $(base_dir)/generators/*/chipyard.mk)


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
FIRESIM_SOURCE_DIRS = $(addprefix sims/firesim/,sim/firesim-lib sim/midas/targetutils) $(addprefix generators/firechip/,chip bridgeinterfaces bridgestubs) tools/firrtl2
CHIPYARD_SOURCE_DIRS = \
	$(filter-out $(base_dir)/generators/firechip,$(wildcard $(addprefix $(base_dir)/,generators/* fpga/fpga-shells fpga/src tools/stage))) \
	$(addprefix $(base_dir)/,$(FIRESIM_SOURCE_DIRS))
CHIPYARD_SCALA_SOURCES = $(call lookup_srcs_by_multiple_type,$(CHIPYARD_SOURCE_DIRS),$(SCALA_EXT))
CHIPYARD_VLOG_SOURCES = $(call lookup_srcs_by_multiple_type,$(CHIPYARD_SOURCE_DIRS),$(VLOG_EXT))
TAPEOUT_SOURCE_DIRS = $(addprefix $(base_dir)/,tools/tapeout)
TAPEOUT_SCALA_SOURCES = $(call lookup_srcs_by_multiple_type,$(TAPEOUT_SOURCE_DIRS),$(SCALA_EXT))
TAPEOUT_VLOG_SOURCES = $(call lookup_srcs_by_multiple_type,$(TAPEOUT_SOURCE_DIRS),$(VLOG_EXT))
# This assumes no SBT meta-build sources
SBT_SOURCE_DIRS = $(addprefix $(base_dir)/,generators tools)
SBT_SOURCES = $(call lookup_srcs,$(SBT_SOURCE_DIRS),sbt) $(base_dir)/build.sbt $(base_dir)/project/plugins.sbt $(base_dir)/project/build.properties

$(build_dir):
	mkdir -p $@

#########################################################################################
# compile scala jars
#########################################################################################
$(GENERATOR_CLASSPATH) &: $(CHIPYARD_SCALA_SOURCES) $(SCALA_BUILDTOOL_DEPS) $(CHIPYARD_VLOG_SOURCES)
	mkdir -p $(dir $@)
	$(call run_sbt_assembly,$(SBT_PROJECT),$(GENERATOR_CLASSPATH))

# order only dependency between sbt runs needed to avoid concurrent sbt runs
$(TAPEOUT_CLASSPATH) &: $(TAPEOUT_SCALA_SOURCES) $(SCALA_BUILDTOOL_DEPS) $(TAPEOUT_VLOG_SOURCES) | $(GENERATOR_CLASSPATH)
	mkdir -p $(dir $@)
	$(call run_sbt_assembly,tapeout,$(TAPEOUT_CLASSPATH))

#########################################################################################
# verilog generation pipeline
#########################################################################################
# AG: must re-elaborate if cva6 sources have changed... otherwise just run firrtl compile
$(FIRRTL_FILE) $(ANNO_FILE) $(CHISEL_LOG_FILE) &: $(GENERATOR_CLASSPATH) $(EXTRA_GENERATOR_REQS)
	mkdir -p $(build_dir)
	(set -o pipefail && $(call run_jar_scala_main,$(GENERATOR_CLASSPATH),$(GENERATOR_PACKAGE).Generator,\
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
export mfc_extra_anno_contents
export sfc_extra_low_transforms_anno_contents
$(FINAL_ANNO_FILE) $(MFC_EXTRA_ANNO_FILE) &: $(ANNO_FILE)
	echo "$$mfc_extra_anno_contents" > $(MFC_EXTRA_ANNO_FILE)
ifdef USE_CHISEL7
	jq '. + [{"class":"firrtl.transforms.BlackBoxTargetDirAnno","targetDir":"$(GEN_COLLATERAL_DIR)/blackboxes"}]' $(MFC_EXTRA_ANNO_FILE) > $(MFC_EXTRA_ANNO_FILE).tmp && mv $(MFC_EXTRA_ANNO_FILE).tmp $(MFC_EXTRA_ANNO_FILE)
endif
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
	$(MFC_FILELIST) \
	$(MFC_BB_MODS_FILELIST) \
	$(GEN_COLLATERAL_DIR) \
	$(FIRTOOL_LOG_FILE)

MFC_BASE_LOWERING_OPTIONS ?= emittedLineLength=2048,noAlwaysComb,disallowLocalVariables,verifLabels,disallowPortDeclSharing,locationInfoStyle=wrapInAtSquareBracket

# Extra firtool flags are only applied when building with Chisel 7
FIRTOOL_EXTRA_FLAGS ?=
ifdef USE_CHISEL7
FIRTOOL_EXTRA_FLAGS += --verification-flavor=if-else-fatal --disable-layers=Verification.Assume,Verification.Cover
endif

# DOC include start: FirrtlCompiler
$(MFC_LOWERING_OPTIONS):
	mkdir -p $(dir $@)
ifeq (,$(ENABLE_YOSYS_FLOW))
	echo "$(MFC_BASE_LOWERING_OPTIONS)" > $@
else
	echo "$(MFC_BASE_LOWERING_OPTIONS),disallowPackedArrays" > $@
endif

$(SFC_MFC_TARGETS) &: $(FIRRTL_FILE) $(FINAL_ANNO_FILE) $(MFC_LOWERING_OPTIONS)
	$(call require_cmd,$(FIRTOOL_BIN))
	$(require_firtool_version)
	rm -rf $(GEN_COLLATERAL_DIR)
	(set -o pipefail && $(FIRTOOL_BIN) \
			--format=fir \
			--export-module-hierarchy \
			--verify-each=true \
			--warn-on-unprocessed-annotations \
			--disable-annotation-classless \
			--disable-annotation-unknown \
			--mlir-timing \
			--lowering-options=$(shell cat $(MFC_LOWERING_OPTIONS)) \
			--repl-seq-mem \
			--repl-seq-mem-file=$(MFC_SMEMS_CONF) \
			--annotation-file=$(FINAL_ANNO_FILE) \
			--split-verilog \
			$(FIRTOOL_EXTRA_FLAGS) \
			-o $(GEN_COLLATERAL_DIR) \
			$(FIRRTL_FILE) |& tee $(FIRTOOL_LOG_FILE))
	$(SED) $(SED_INPLACE) 's/.*/& /' $(MFC_SMEMS_CONF) # need trailing space for SFC macrocompiler
ifdef USE_CHISEL7
	# Construct blackbox file list from files emitted into gen-collateral/blackboxes
	@if [ -d "$(GEN_COLLATERAL_DIR)/blackboxes" ]; then \
	  find "$(GEN_COLLATERAL_DIR)/blackboxes" -type f \( -name '*.v' -o -name '*.sv' -o -name '*.cc' \) | \
	    sed -e 's;^$(GEN_COLLATERAL_DIR)/;;' > "$(MFC_BB_MODS_FILELIST)"; \
	else \
	  : > "$(MFC_BB_MODS_FILELIST)"; \
	fi
else
	# If there are no BB's then the file might not be generated; ensure it exists
	touch $(MFC_BB_MODS_FILELIST)
endif
# DOC include end: FirrtlCompiler

.PHONY: run-firtool
run-firtool: $(SFC_MFC_TARGETS)
	@echo "[run-firtool] Generated: $(SFC_MFC_TARGETS)"

# Convenience alias to re-run the uniquify step (module/filelist splitting)
.PHONY: run-uniquify
run-uniquify: $(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(ALL_MODS_FILELIST) $(BB_MODS_FILELIST) $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED)
	@echo "[run-uniquify] Updated filelists under $(GEN_COLLATERAL_DIR)"


$(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(ALL_MODS_FILELIST) $(BB_MODS_FILELIST) $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED) &: $(MFC_MODEL_HRCHY_JSON) $(MFC_TOP_HRCHY_JSON) $(MFC_FILELIST) $(MFC_BB_MODS_FILELIST)
	$(base_dir)/scripts/uniquify-module-names.py \
		--model-hier-json $(MFC_MODEL_HRCHY_JSON) \
		--top-hier-json $(MFC_TOP_HRCHY_JSON) \
		--in-all-filelist $(MFC_FILELIST) \
		--in-bb-filelist $(MFC_BB_MODS_FILELIST) \
		--dut $(TOP) \
		--model $(MODEL) \
		--target-dir $(GEN_COLLATERAL_DIR) \
		--out-dut-filelist $(TOP_MODS_FILELIST) \
		--out-model-filelist $(MODEL_MODS_FILELIST) \
		--out-model-hier-json $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED) \
		--gcpath $(GEN_COLLATERAL_DIR)
	$(SED) -e 's;^;$(GEN_COLLATERAL_DIR)/;' $(MFC_BB_MODS_FILELIST) > $(BB_MODS_FILELIST)
	$(SED) $(SED_INPLACE) 's/\.\///' $(TOP_MODS_FILELIST)
	$(SED) $(SED_INPLACE) 's/\.\///' $(MODEL_MODS_FILELIST)
	$(SED) $(SED_INPLACE) 's/\.\///' $(BB_MODS_FILELIST)
	sort -u $(TOP_MODS_FILELIST) $(MODEL_MODS_FILELIST) $(BB_MODS_FILELIST) > $(ALL_MODS_FILELIST)

$(TOP_SMEMS_CONF) $(MODEL_SMEMS_CONF) &:  $(MFC_SMEMS_CONF) $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED)
	$(base_dir)/scripts/split-mems-conf.py \
		--in-smems-conf $(MFC_SMEMS_CONF) \
		--in-model-hrchy-json $(MFC_MODEL_HRCHY_JSON_UNIQUIFIED) \
		--dut-module-name $(TOP) \
		--model-module-name $(MODEL) \
		--out-dut-smems-conf $(TOP_SMEMS_CONF) \
		--out-model-smems-conf $(MODEL_SMEMS_CONF)
#	for blackboxed SRAMs: add custom.mems.conf as blackbox and use generated module name in blackbox verilog source
	-[ -f $(GEN_COLLATERAL_DIR)/custom.mems.conf ] && cat $(GEN_COLLATERAL_DIR)/custom.mems.conf >> $(TOP_SMEMS_CONF)

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
TOP_MACROCOMPILER_MODE ?= --mode synflops
$(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR) &: $(TAPEOUT_CLASSPATH) $(TOP_SMEMS_CONF)
	$(call run_jar_scala_main,$(TAPEOUT_CLASSPATH),tapeout.macros.MacroCompiler,-n $(TOP_SMEMS_CONF) -v $(TOP_SMEMS_FILE) -f $(TOP_SMEMS_FIR) $(TOP_MACROCOMPILER_MODE))
	touch $(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR)

MODEL_MACROCOMPILER_MODE = --mode synflops
$(MODEL_SMEMS_FILE) $(MODEL_SMEMS_FIR) &: $(TAPEOUT_CLASSPATH) $(MODEL_SMEMS_CONF)
	$(call run_jar_scala_main,$(TAPEOUT_CLASSPATH),tapeout.macros.MacroCompiler, -n $(MODEL_SMEMS_CONF) -v $(MODEL_SMEMS_FILE) -f $(MODEL_SMEMS_FIR) $(MODEL_MACROCOMPILER_MODE))
	touch $(MODEL_SMEMS_FILE) $(MODEL_SMEMS_FIR)

########################################################################################
# remove duplicate files and headers in list of simulation file inputs
# note: {MODEL,TOP}_BB_MODS_FILELIST is added as a req. so that the files get generated,
#       however it is really unneeded since ALL_MODS_FILELIST includes all BB files
########################################################################################
$(sim_common_files): $(sim_files) $(ALL_MODS_FILELIST) $(TOP_SMEMS_FILE) $(MODEL_SMEMS_FILE) $(BB_MODS_FILELIST) $(EXT_FILELISTS)
ifneq (,$(EXT_FILELISTS))
	cat $(EXT_FILELISTS) > $@
else
	rm -f $@
endif
	sort -u $(sim_files) $(ALL_MODS_FILELIST) | grep -v '.*\.\(svh\|h\|conf\)$$' >> $@
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
get_sim_out_name = $(output_dir)/$(call get_out_name,$(1))$(if $(EXTRA_SIM_OUT_NAME),.$(EXTRA_SIM_OUT_NAME),)
# sim flags that are common to run-binary/run-binary-fast/run-binary-debug
get_common_sim_flags = $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(SEED_FLAG) $(call get_loadmem_flag,$(1)) $(call get_loadarch_flag,$(1))

.PHONY: %.run %.run.debug %.run.fast

# run normal binary with hardware-logged insn dissassembly
run-binary: check-binary $(BINARY).run
run-binaries: check-binaries $(addsuffix .run,$(wildcard $(BINARIES)))

%.run: %.check-exists $(SIM_PREREQ) | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) \
		$(PERMISSIVE_ON) \
		$(call get_common_sim_flags,$*) \
		$(VERBOSE_FLAGS) \
		$(PERMISSIVE_OFF) \
		$* \
		$(BINARY_ARGS) \
		</dev/null 2> >(spike-dasm > $(call get_sim_out_name,$*).out) | tee $(call get_sim_out_name,$*).log)

# run simulator as fast as possible (no insn disassembly)
run-binary-fast: check-binary $(BINARY).run.fast
run-binaries-fast: check-binaries $(addsuffix .run.fast,$(wildcard $(BINARIES)))

%.run.fast: %.check-exists $(SIM_PREREQ) | $(output_dir)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) \
		$(PERMISSIVE_ON) \
		$(call get_common_sim_flags,$*) \
		$(PERMISSIVE_OFF) \
		$* \
		$(BINARY_ARGS) \
		</dev/null | tee $(call get_sim_out_name,$*).log)

# run simulator with as much debug info as possible
run-binary-debug: check-binary $(BINARY).run.debug
run-binary-debug-bg: check-binary $(BINARY).run.debug.bg
run-binaries-debug: check-binaries $(addsuffix .run.debug,$(wildcard $(BINARIES)))
run-binaries-debug-bg: check-binaries $(addsuffix .run.debug.bg,$(wildcard $(BINARIES)))

%.run.debug: %.check-exists $(SIM_DEBUG_PREREQ) | $(output_dir)
ifeq (1,$(DUMP_BINARY))
	if [ "$*" != "none" ]; then riscv64-unknown-elf-objdump -D -S $* > $(call get_sim_out_name,$*).dump ; fi
endif
	(set -o pipefail && $(NUMA_PREFIX) $(sim_debug) \
		$(PERMISSIVE_ON) \
		$(call get_common_sim_flags,$*) \
		$(VERBOSE_FLAGS) \
		$(call get_waveform_flag,$(call get_sim_out_name,$*)) \
		$(PERMISSIVE_OFF) \
		$* \
		$(BINARY_ARGS) \
		</dev/null 2> >(spike-dasm > $(call get_sim_out_name,$*).out) | tee $(call get_sim_out_name,$*).log)

%.run.debug.bg: %.check-exists $(SIM_DEBUG_PREREQ) | $(output_dir)
	if [ "$*" != "none" ]; then riscv64-unknown-elf-objdump -D -S $* > $(call get_sim_out_name,$*).dump ; fi
	(set -o pipefail && $(NUMA_PREFIX) $(sim_debug) \
		$(PERMISSIVE_ON) \
		$(call get_common_sim_flags,$*) \
		$(VERBOSE_FLAGS) \
		$(call get_waveform_flag,$(call get_sim_out_name,$*)) \
		$(PERMISSIVE_OFF) \
		$* \
		$(BINARY_ARGS) \
		</dev/null 2> >(spike-dasm > $(call get_sim_out_name,$*).out) >$(call get_sim_out_name,$*).log \
		& echo "PID=$$!")

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

ifdef RISCV
$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/% | $(output_dir)
	ln -sf $< $@
endif

$(output_dir)/%.run: $(output_dir)/% $(SIM_PREREQ)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(call get_common_sim_flags,$<) $(PERMISSIVE_OFF)  $< </dev/null | tee $<.log) && touch $@

$(output_dir)/%.out: $(output_dir)/% $(SIM_PREREQ)
	(set -o pipefail && $(NUMA_PREFIX) $(sim) $(PERMISSIVE_ON) $(call get_common_sim_flags,$<) $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $@) | tee $<.log)

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

.PHONY: find-configs
find-configs:
	$(call run_scala_main,chipyard,chipyard.ChipyardConfigFinder,)

.PHONY: help
help:
	@for line in $(HELP_LINES); do echo "$$line"; done

#########################################################################################
# Implicit rule handling
#########################################################################################
# Disable all suffix rules to improve Make performance on systems running older
# versions of Make
.SUFFIXES:

.PHONY: print-%
# Print any variable and it's origin. This helps figure out where the
# variable was defined and to distinguish between empty and undefined.
print-%:
	@echo "$*=$($*)"
	@echo "Origin is: $(origin $*)"
