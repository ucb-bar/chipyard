#########################################################################################
# set default shell for make
#########################################################################################
SHELL=/bin/bash

#########################################################################################
# variables to get all *.scala files
#########################################################################################
lookup_scala_srcs = $(shell find -L $(1)/ -iname "*.scala" 2> /dev/null)

PACKAGES=$(addprefix generators/, rocket-chip testchipip boom hwacha sifive-blocks systolic-array awl beagle sifive-cache example) \
		 $(addprefix sims/firesim/sim/, . firesim-lib midas midas/targetutils)
SCALA_SOURCES=$(foreach pkg,$(PACKAGES),$(call lookup_scala_srcs,$(base_dir)/$(pkg)/src/main/scala))

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
# create simulation args file rule
#########################################################################################
$(sim_dotf): $(call lookup_scala_srcs,$(base_dir)/generators/utilities/src/main/scala) $(FIRRTL_JAR)
	cd $(base_dir) && $(SBT) "project utilities" "runMain utilities.GenerateSimFiles -td $(build_dir) -sim $(sim_name)"

#########################################################################################
# create firrtl file rule and variables
#########################################################################################
$(FIRRTL_FILE) $(ANNO_FILE): $(SCALA_SOURCES) $(sim_dotf)
	mkdir -p $(build_dir)
	cd $(base_dir) && $(SBT) "project $(SBT_PROJECT)" "runMain $(GENERATOR_PACKAGE).Generator $(build_dir) $(MODEL_PACKAGE) $(MODEL) $(CONFIG_PACKAGE) $(CONFIG)"

#########################################################################################
# create verilog files rules and variables
#########################################################################################
REPL_SEQ_MEM = --infer-rw --repl-seq-mem -c:$(MODEL):-o:$(SMEMS_CONF)
HARNESS_REPL_SEQ_MEM = --infer-rw --repl-seq-mem -c:$(MODEL):-o:$(HARNESS_SMEMS_CONF)

$(VERILOG_FILE) $(SMEMS_CONF) $(TOP_ANNO) $(TOP_FIR) $(sim_top_blackboxes): $(FIRRTL_FILE) $(ANNO_FILE)
	cd $(base_dir) && $(SBT) "project tapeout" "runMain barstools.tapeout.transforms.GenerateTop -o $(VERILOG_FILE) -i $(FIRRTL_FILE) --syn-top $(TOP) --harness-top $(MODEL) -faf $(ANNO_FILE) -tsaof $(TOP_ANNO) -tsf $(TOP_FIR) $(REPL_SEQ_MEM) -td $(build_dir)"
	cp $(build_dir)/firrtl_black_box_resource_files.f $(sim_top_blackboxes)

$(HARNESS_FILE) $(HARNESS_ANNO) $(HARNESS_FIR) $(sim_harness_blackboxes): $(FIRRTL_FILE) $(ANNO_FILE) $(sim_top_blackboxes)
	cd $(base_dir) && $(SBT) "project tapeout" "runMain barstools.tapeout.transforms.GenerateHarness -o $(HARNESS_FILE) -i $(FIRRTL_FILE) --syn-top $(TOP) --harness-top $(VLOG_MODEL) -faf $(ANNO_FILE) -thaof $(HARNESS_ANNO) -thf $(HARNESS_FIR) $(HARNESS_REPL_SEQ_MEM) -td $(build_dir)"
	grep -v ".*\.cc" $(build_dir)/firrtl_black_box_resource_files.f > $(sim_harness_blackboxes)

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
MACROCOMPILER_MODE ?= --mode synflops
$(SMEMS_FILE) $(SMEMS_FIR): $(SMEMS_CONF)
	cd $(base_dir) && $(SBT) "project barstoolsMacros" "runMain barstools.macros.MacroCompiler -n $(SMEMS_CONF) -v $(SMEMS_FILE) -f $(SMEMS_FIR) $(MACROCOMPILER_MODE)"

HARNESS_MACROCOMPILER_MODE = --mode synflops
$(HARNESS_SMEMS_FILE) $(HARNESS_SMEMS_FIR): $(HARNESS_SMEMS_CONF)
	cd $(base_dir) && $(SBT) "project barstoolsMacros" "runMain barstools.macros.MacroCompiler -n $(HARNESS_SMEMS_CONF) -v $(HARNESS_SMEMS_FILE) -f $(HARNESS_SMEMS_FIR) $(HARNESS_MACROCOMPILER_MODE)"

#########################################################################################
# helper rule to just make verilog files
#########################################################################################
.PHONY: verilog top
verilog: $(sim_vsrcs)

top: $(VERILOG_FILE) $(SMEMS_CONF)

#########################################################################################
# helper rules to run simulator
#########################################################################################
run-binary: $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) $(SIM_FLAGS) $(PERMISSIVE_OFF) $(BINARY) 3>&1 1>&2 2>&3 | spike-dasm > $(sim_out_name).out)

#########################################################################################
# run assembly/benchmarks rules
#########################################################################################
$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -sf $< $@

$(output_dir)/%.run: $(output_dir)/% $(sim)
	$(sim) $(PERMISSIVE_ON) +max-cycles=$(timeout_cycles) $(PERMISSIVE_OFF) $< && touch $@

$(output_dir)/%.out: $(output_dir)/% $(sim)
	(set -o pipefail && $(sim) $(PERMISSIVE_ON) +verbose +max-cycles=$(timeout_cycles) $(PERMISSIVE_OFF) $< 3>&1 1>&2 2>&3 | spike-dasm > $@)

#########################################################################################
# include build/project specific makefrags made from the generator
#########################################################################################
ifneq ($(filter run% %.run %.out %.vpd %.vcd,$(MAKECMDGOALS)),)
-include $(build_dir)/$(long_name).d
endif

#########################################################################################
# default regression tests variables and rules
# TODO: Remove in favor of each project having its own regression tests?
#########################################################################################
regression-tests = \
	rv64ud-v-fcvt \
        rv64ud-p-fdiv \
        rv64ud-v-fadd \
        rv64uf-v-fadd \
        rv64um-v-mul \
        rv64mi-p-breakpoint \
        rv64uc-v-rvc \
        rv64ud-v-structural \
        rv64si-p-wfi \
        rv64um-v-divw \
        rv64ua-v-lrsc \
        rv64ui-v-fence_i \
        rv64ud-v-fcvt_w \
        rv64uf-v-fmin \
        rv64ui-v-sb \
        rv64ua-v-amomax_d \
        rv64ud-v-move \
        rv64ud-v-fclass \
        rv64ua-v-amoand_d \
        rv64ua-v-amoxor_d \
        rv64si-p-sbreak \
        rv64ud-v-fmadd \
        rv64uf-v-ldst \
        rv64um-v-mulh \
        rv64si-p-dirty

.PHONY: run-regression-tests run-regression-tests-fast run-regression-tests-debug
run-regression-tests: $(addprefix $(output_dir)/,$(addsuffix .out,$(regression-tests)))
run-regression-tests-fast: $(addprefix $(output_dir)/,$(addsuffix .run,$(regression-tests)))
run-regression-tests-debug: $(addprefix $(output_dir)/,$(addsuffix .vpd,$(regression-tests)))
