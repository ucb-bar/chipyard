#########################################################################################
# set default shell for make
#########################################################################################
SHELL=/bin/bash

#########################################################################################
# extra make variables/rules from subprojects
#
# EXTRA_GENERATOR_REQS - requirements needed for the main generator
# EXTRA_SIM_FLAGS - runtime simulation flags
# EXTRA_SIM_CC_FLAGS - cc flags for simulators
# EXTRA_SIM_SOURCES - simulation sources needed for simulator
# EXTRA_SIM_REQS - requirements to build the simulator
#########################################################################################
include $(base_dir)/generators/ariane/ariane.mk
include $(base_dir)/generators/tracegen/tracegen.mk

#########################################################################################
# variables to get all *.scala files
#########################################################################################
lookup_srcs = $(shell find -L $(1)/ -name target -prune -o -iname "*.$(2)" -print 2> /dev/null)

SOURCE_DIRS = $(addprefix $(base_dir)/,generators sims/firesim/sim tools/barstools/iocell)
SCALA_SOURCES = $(call lookup_srcs,$(SOURCE_DIRS),scala)
VLOG_SOURCES = $(call lookup_srcs,$(SOURCE_DIRS),sv) $(call lookup_srcs,$(SOURCE_DIRS),v)

#########################################################################################
# rocket and testchipip classes
#########################################################################################
# NB: target/ lives under source ----V , due to how we're handling midas dependency injection
ROCKET_CLASSES ?= "$(ROCKETCHIP_DIR)/src/target/scala-$(SCALA_VERSION_MAJOR)/classes:$(ROCKETCHIP_DIR)/chisel3/target/scala-$(SCALA_VERSION_MAJOR)/*"
TESTCHIPIP_CLASSES ?= "$(TESTCHIP_DIR)/target/scala-$(SCALA_VERSION_MAJOR)/classes"

#########################################################################################
# jar creation variables and rules
#########################################################################################
FIRRTL_JAR := $(base_dir)/lib/firrtl.jar

$(FIRRTL_JAR): $(call lookup_scala_srcs, $(CHIPYARD_FIRRTL_DIR)/src/main/scala)
	$(MAKE) -C $(CHIPYARD_FIRRTL_DIR) SBT="$(SBT)" root_dir=$(CHIPYARD_FIRRTL_DIR) build-scala
	mkdir -p $(@D)
	cp -p $(CHIPYARD_FIRRTL_DIR)/utils/bin/firrtl.jar $@
	touch $@

#########################################################################################
# create list of simulation file inputs
#########################################################################################
$(sim_files): $(call lookup_scala_srcs,$(base_dir)/generators/utilities/src/main/scala) $(FIRRTL_JAR)
	cd $(base_dir) && $(SBT) "project utilities" "runMain utilities.GenerateSimFiles -td $(build_dir) -sim $(sim_name)"

#########################################################################################
# create firrtl file rule and variables
#########################################################################################
.INTERMEDIATE: generator_temp
$(FIRRTL_FILE) $(ANNO_FILE): generator_temp
	@echo "" > /dev/null

# AG: must re-elaborate if ariane sources have changed... otherwise just run firrtl compile
generator_temp: $(SCALA_SOURCES) $(sim_files) $(EXTRA_GENERATOR_REQS)
	mkdir -p $(build_dir)
	cd $(base_dir) && $(SBT) "project $(SBT_PROJECT)" "runMain $(GENERATOR_PACKAGE).Generator \
		--target-dir $(build_dir) \
		--name $(long_name) \
		--top-module $(MODEL_PACKAGE).$(MODEL) \
		--legacy-configs $(CONFIG_PACKAGE).$(CONFIG)"

.PHONY: firrtl
firrtl: $(FIRRTL_FILE)

#########################################################################################
# create verilog files rules and variables
#########################################################################################
REPL_SEQ_MEM = --infer-rw --repl-seq-mem -c:$(MODEL):-o:$(TOP_SMEMS_CONF)
HARNESS_CONF_FLAGS = -thconf $(HARNESS_SMEMS_CONF)

TOP_TARGETS = $(TOP_FILE) $(TOP_SMEMS_CONF) $(TOP_ANNO) $(TOP_FIR) $(sim_top_blackboxes)
HARNESS_TARGETS = $(HARNESS_FILE) $(HARNESS_SMEMS_CONF) $(HARNESS_ANNO) $(HARNESS_FIR) $(sim_harness_blackboxes)

# DOC include start: FirrtlCompiler
# NOTE: These *_temp intermediate targets will get removed in favor of make 4.3 grouped targets (&: operator)
.INTERMEDIATE: firrtl_temp
$(TOP_TARGETS) $(HARNESS_TARGETS): firrtl_temp
	@echo "" > /dev/null

firrtl_temp: $(FIRRTL_FILE) $(ANNO_FILE) $(VLOG_SOURCES)
	cd $(base_dir) && $(SBT) "project tapeout" "runMain barstools.tapeout.transforms.GenerateTopAndHarness -o $(TOP_FILE) -tho $(HARNESS_FILE) -i $(FIRRTL_FILE) --syn-top $(TOP) --harness-top $(VLOG_MODEL) -faf $(ANNO_FILE) -tsaof $(TOP_ANNO) -tdf $(sim_top_blackboxes) -tsf $(TOP_FIR) -thaof $(HARNESS_ANNO) -hdf $(sim_harness_blackboxes) -thf $(HARNESS_FIR) $(REPL_SEQ_MEM) $(HARNESS_CONF_FLAGS) -td $(build_dir)" && touch $(sim_top_blackboxes) $(sim_harness_blackboxes)
# DOC include end: FirrtlCompiler

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
MACROCOMPILER_MODE ?= --mode synflops
.INTERMEDIATE: top_macro_temp
$(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR): top_macro_temp
	@echo "" > /dev/null

top_macro_temp: $(TOP_SMEMS_CONF)
	cd $(base_dir) && $(SBT) "project barstoolsMacros" "runMain barstools.macros.MacroCompiler -n $(TOP_SMEMS_CONF) -v $(TOP_SMEMS_FILE) -f $(TOP_SMEMS_FIR) $(MACROCOMPILER_MODE)"

HARNESS_MACROCOMPILER_MODE = --mode synflops
.INTERMEDIATE: harness_macro_temp
$(HARNESS_SMEMS_FILE) $(HARNESS_SMEMS_FIR): harness_macro_temp
	@echo "" > /dev/null

harness_macro_temp: $(HARNESS_SMEMS_CONF) | top_macro_temp
	cd $(base_dir) && $(SBT) "project barstoolsMacros" "runMain barstools.macros.MacroCompiler -n $(HARNESS_SMEMS_CONF) -v $(HARNESS_SMEMS_FILE) -f $(HARNESS_SMEMS_FIR) $(HARNESS_MACROCOMPILER_MODE)"

########################################################################################
# remove duplicate files and headers in list of simulation file inputs
########################################################################################
$(sim_common_files): $(sim_files) $(sim_top_blackboxes) $(sim_harness_blackboxes)
	awk '{print $1;}' $^ | sort -u | grep -v '.*\.\(svh\|h\)$$' > $@

#########################################################################################
# helper rule to just make verilog files
#########################################################################################
.PHONY: verilog
verilog: $(sim_vsrcs)

#########################################################################################
# helper rules to run simulations
#########################################################################################
.PHONY: run-binary run-binary-fast run-binary-debug run-fast
run-binary: $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $(BINARY) </dev/null 2> >(spike-dasm > $(sim_out_name).out) | tee $(sim_out_name).log)

#########################################################################################
# helper rules to run simulator as fast as possible
#########################################################################################
run-binary-fast: $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(PERMISSIVE_OFF) $(BINARY) </dev/null | tee $(sim_out_name).log)

#########################################################################################
# helper rules to run simulator with as much debug info as possible
#########################################################################################
run-binary-debug: $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) $(WAVEFORM_FLAG) $(PERMISSIVE_OFF) $(BINARY) </dev/null 2> >(spike-dasm > $(sim_out_name).out) | tee $(sim_out_name).log)

run-fast: run-asm-tests-fast run-bmark-tests-fast

run-none: $(output_dir)/none.out

run-none-fast: $(output_dir)/none.run

run-none-debug: $(output_dir)/none.vpd

#########################################################################################
# run assembly/benchmarks rules
#########################################################################################
$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -sf $< $@

$(output_dir)/%.run: $(output_dir)/% $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(PERMISSIVE_OFF) $< </dev/null | tee $<.log) && touch $@

$(output_dir)/%.out: $(output_dir)/% $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $@) | tee $<.log)

$(output_dir)/none.run: $(sim)
	mkdir -p $(output_dir)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(PERMISSIVE_OFF) $< </dev/null | tee $<.log) && touch $@

$(output_dir)/none.out: $(sim)
	mkdir -p $(output_dir)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) none </dev/null 2> >(spike-dasm > $@) | tee $(output_dir)/none.log)

#########################################################################################
# include build/project specific makefrags made from the generator
#########################################################################################
ifneq ($(filter run% %.run %.out %.vpd %.vcd,$(MAKECMDGOALS)),)
-include $(build_dir)/$(long_name).d
endif

#######################################
# Rules for building DRAMSim2 library #
#######################################

dramsim_dir = $(base_dir)/tools/DRAMSim2
dramsim_lib = $(dramsim_dir)/libdramsim.a

$(dramsim_lib):
	$(MAKE) -C $(dramsim_dir) $(notdir $@)
