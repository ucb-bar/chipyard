#########################################################################################
# set default shell for make
#########################################################################################
SHELL=/bin/bash

#########################################################################################
# variables to get all *.scala files
#########################################################################################
lookup_scala_srcs = $(shell find $(1)/ -iname "*.scala" 2> /dev/null)

PACKAGES=rocket-chip testchipip
SCALA_SOURCES=$(foreach pkg,$(PACKAGES),$(call lookup_scala_srcs,$(base_dir)/generators/$(pkg)/src/main/scala)) $(call lookup_scala_srcs,$(base_dir)/src/main/scala)

#########################################################################################
# rocket and testchipip classes
#########################################################################################
ROCKET_CLASSES ?= "$(ROCKETCHIP_DIR)/target/scala-$(SCALA_VERSION_MAJOR)/classes:$(ROCKETCHIP_DIR)/chisel3/target/scala-$(SCALA_VERSION_MAJOR)/*"
TESTCHIPIP_CLASSES ?= "$(TESTCHIP_DIR)/target/scala-$(SCALA_VERSION_MAJOR)/classes"

#########################################################################################
# jar creation variables and rules
#########################################################################################
FIRRTL_JAR ?= $(ROCKETCHIP_DIR)/lib/firrtl.jar

# this should match whatever the commonSettings version is in build.sbt
BARSTOOLS_VER=1.0
TAPEOUT_JAR=$(base_dir)/tools/barstools/tapeout/target/scala-$(SCALA_VERSION_MAJOR)/tapeout-assembly-$(BARSTOOLS_VER).jar
MACROCOMPILER_JAR=$(base_dir)/tools/barstools/macros/target/scala-$(SCALA_VERSION_MAJOR)/barstools-macros-assembly-$(BARSTOOLS_VER).jar

$(FIRRTL_JAR): $(call lookup_scala_srcs, $(ROCKETCHIP_DIR)/firrtl/src/main/scala)
	$(MAKE) -C $(ROCKETCHIP_DIR)/firrtl SBT="$(SBT)" root_dir=$(ROCKETCHIP_DIR)/firrtl build-scala
	mkdir -p $(dir $@)
	cp -p $(ROCKETCHIP_DIR)/firrtl/utils/bin/firrtl.jar $@
	touch $@

$(TAPEOUT_JAR): $(call lookup_scala_srcs, $(base_dir)/tools/barstools/tapeout/src/main/scala)
	cd $(base_dir) && $(SBT) "tapeout/assembly"

$(MACROCOMPILER_JAR): $(call lookup_scala_srcs, $(base_dir)/tools/barstools/macros/src/main/scala) $(call lookup_scala_srcs, $(base_dir)/tools/barstools/mdf/scalalib/src/main/scala)
	cd $(base_dir) && $(SBT) "barstools-macros/assembly"

.PHONY: jars
jars: $(MACROCOMPILER_JAR) $(TAPEOUT_JAR)

#########################################################################################
# tapeout and macrocompiler commands
#########################################################################################
TAPEOUT ?= java -Xmx8G -Xss8M -cp $(ROCKET_CLASSES):$(TESTCHIPIP_CLASSES):$(TAPEOUT_JAR)
MACROCOMPILER ?= java -Xmx8G -Xss8M -cp $(ROCKET_CLASSES):$(TESTCHIPIP_CLASSES):$(MACROCOMPILER_JAR)

#########################################################################################
# create simulation args file rule
#########################################################################################
$(sim_dotf): $(SCALA_SOURCES) $(FIRRTL_JAR)
	cd $(base_dir) && $(SBT) "runMain example.GenerateSimFiles -td $(build_dir) -sim $(sim_name)"

#########################################################################################
# create firrtl file rule and variables
#########################################################################################
CHISEL_ARGS ?=

$(FIRRTL_FILE) $(ANNO_FILE): $(SCALA_SOURCES) $(sim_dotf)
	mkdir -p $(build_dir)
	cd $(base_dir) && $(SBT) "runMain $(PROJECT).Generator $(CHISEL_ARGS) $(build_dir) $(PROJECT) $(MODEL) $(CFG_PROJECT) $(CONFIG)"

#########################################################################################
# create verilog files rules and variables
#########################################################################################
REPL_SEQ_MEM = --repl-seq-mem -c:$(MODEL):-o:$(SMEMS_CONF)

$(VERILOG_FILE) $(SMEMS_CONF): $(FIRRTL_FILE) $(ANNO_FILE) $(TAPEOUT_JAR)
	$(TAPEOUT) barstools.tapeout.transforms.GenerateTop -o $(VERILOG_FILE) -i $(FIRRTL_FILE) --syn-top $(TOP) --harness-top $(MODEL) -faf $(ANNO_FILE) $(REPL_SEQ_MEM) -td $(build_dir)

$(HARNESS_FILE): $(FIRRTL_FILE) $(ANNO_FILE) $(TAPEOUT_JAR)
	$(TAPEOUT) barstools.tapeout.transforms.GenerateHarness -o $(HARNESS_FILE) -i $(FIRRTL_FILE) --syn-top $(TOP) --harness-top $(MODEL) -faf $(ANNO_FILE) -td $(build_dir)

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
$(SMEMS_FILE): $(SMEMS_CONF) $(MACROCOMPILER_JAR)
	$(MACROCOMPILER) barstools.macros.MacroCompiler -n $(SMEMS_CONF) -v $(SMEMS_FILE) --mode synflops

#########################################################################################
# helper rule to just make verilog files
#########################################################################################
.PHONY: verilog
verilog: $(sim_vsrcs)

#########################################################################################
# run assembly/benchmarks rules
#########################################################################################
$(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -sf $< $@

$(output_dir)/%.run: $(output_dir)/% $(sim)
	$(sim) +max-cycles=$(timeout_cycles) $< && touch $@

$(output_dir)/%.out: $(output_dir)/% $(sim)
	$(sim) +verbose +max-cycles=$(timeout_cycles) $< 3>&1 1>&2 2>&3 | spike-dasm > $@

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

#########################################################################################
# general jar cleanup rule
#########################################################################################
.PHONY: clean-scala
clean-scala:
	rm -rf $(MACROCOMPILER_JAR) $(TAPEOUT_JAR)

