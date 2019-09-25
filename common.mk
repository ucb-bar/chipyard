#########################################################################################
# set default shell for make
#########################################################################################
SHELL=/bin/bash

#########################################################################################
# variables to get all *.scala files
#########################################################################################
lookup_scala_srcs = $(shell find -L $(1)/ -name target -prune -o -iname "*.scala" -print 2> /dev/null)

SOURCE_DIRS=$(addprefix $(base_dir)/,generators sims/firesim/sim)
SCALA_SOURCES=$(call lookup_scala_srcs,$(SOURCE_DIRS))

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
generator_temp: $(SCALA_SOURCES) $(sim_files)
	mkdir -p $(build_dir)
	cd $(base_dir) && $(SBT) "project $(SBT_PROJECT)" "runMain $(GENERATOR_PACKAGE).Generator $(build_dir) $(MODEL_PACKAGE) $(MODEL) $(CONFIG_PACKAGE) $(CONFIG)"

#########################################################################################
# create verilog files rules and variables
#########################################################################################
REPL_SEQ_MEM = --infer-rw --repl-seq-mem -c:$(MODEL):-o:$(TOP_SMEMS_CONF)
HARNESS_CONF_FLAGS = -thconf $(HARNESS_SMEMS_CONF)

TOP_TARGETS = $(TOP_FILE) $(TOP_SMEMS_CONF) $(TOP_ANNO) $(TOP_FIR) $(sim_top_blackboxes)
HARNESS_TARGETS = $(HARNESS_FILE) $(HARNESS_SMEMS_CONF) $(HARNESS_ANNO) $(HARNESS_FIR) $(sim_harness_blackboxes)

# DOC include start: FirrtlCompiler
.INTERMEDIATE: firrtl_temp
$(TOP_TARGETS) $(HARNESS_TARGETS): firrtl_temp
firrtl_temp: $(FIRRTL_FILE) $(ANNO_FILE)
	cd $(base_dir) && $(SBT) "project tapeout" "runMain barstools.tapeout.transforms.GenerateTopAndHarness -o $(TOP_FILE) -tho $(HARNESS_FILE) -i $(FIRRTL_FILE) --syn-top $(TOP) --harness-top $(VLOG_MODEL) -faf $(ANNO_FILE) -tsaof $(TOP_ANNO) -tdf $(sim_top_blackboxes) -tsf $(TOP_FIR) -thaof $(HARNESS_ANNO) -hdf $(sim_harness_blackboxes) -thf $(HARNESS_FIR) $(REPL_SEQ_MEM) $(HARNESS_CONF_FLAGS) -td $(build_dir)"
# DOC include end: FirrtlCompiler

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
MACROCOMPILER_MODE ?= --mode synflops
.INTERMEDIATE: top_macro_temp
$(TOP_SMEMS_FILE) $(TOP_SMEMS_FIR): top_macro_temp
top_macro_temp: $(TOP_SMEMS_CONF)
	cd $(base_dir) && $(SBT) "project barstoolsMacros" "runMain barstools.macros.MacroCompiler -n $(TOP_SMEMS_CONF) -v $(TOP_SMEMS_FILE) -f $(TOP_SMEMS_FIR) $(MACROCOMPILER_MODE)"

HARNESS_MACROCOMPILER_MODE = --mode synflops
.INTERMEDIATE: harness_macro_temp
$(HARNESS_SMEMS_FILE) $(HARNESS_SMEMS_FIR): harness_macro_temp
harness_macro_temp: $(HARNESS_SMEMS_CONF)
	cd $(base_dir) && $(SBT) "project barstoolsMacros" "runMain barstools.macros.MacroCompiler -n $(HARNESS_SMEMS_CONF) -v $(HARNESS_SMEMS_FILE) -f $(HARNESS_SMEMS_FIR) $(HARNESS_MACROCOMPILER_MODE)"

########################################################################################
# remove duplicate files and headers in list of simulation file inputs
########################################################################################
$(sim_common_files): $(sim_files) $(sim_top_blackboxes) $(sim_harness_blackboxes)
	awk '{print $1;}' $^ | sort -u | grep -v '.*\.h' > $@

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
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(SIM_FLAGS) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $(BINARY) 3>&1 1>&2 2>&3 | spike-dasm > $(sim_out_name).out)

#########################################################################################
# helper rules to run simulator as fast as possible
#########################################################################################
run-binary-fast: $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(SIM_FLAGS) $(PERMISSIVE_OFF) $(BINARY) 3>&1 1>&2 2>&3 | spike-dasm > $(sim_out_name).out)

#########################################################################################
# helper rules to run simulator with as much debug info as possible
#########################################################################################
run-binary-debug: $(sim_debug)
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(SIM_FLAGS) $(VERBOSE_FLAGS) $(WAVEFORM_FLAG) $(PERMISSIVE_OFF) $(BINARY) 3>&1 1>&2 2>&3 | spike-dasm > $(sim_out_name).out)

run-fast: run-asm-tests-fast run-bmark-tests-fast

#########################################################################################
# run assembly/benchmarks rules
#########################################################################################
$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -sf $< $@

$(output_dir)/%.run: $(output_dir)/% $(sim)
	$(sim) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(SIM_FLAGS) $(PERMISSIVE_OFF) $< && touch $@

$(output_dir)/%.out: $(output_dir)/% $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(VERBOSE_FLAGS) $(PERMISSIVE_OFF) $< 3>&1 1>&2 2>&3 | spike-dasm > $@)

#########################################################################################
# include build/project specific makefrags made from the generator
#########################################################################################
ifneq ($(filter run% %.run %.out %.vpd %.vcd,$(MAKECMDGOALS)),)
-include $(build_dir)/$(long_name).d
endif
