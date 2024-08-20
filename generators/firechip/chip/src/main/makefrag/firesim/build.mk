# See LICENSE for license details.

CHIPYARD_STAGING_DIR := $(chipyard_dir)/sims/firesim-staging

# target scala directories to copy into midas. used by TARGET_COPY_TO_MIDAS_SCALA_DIRS
TARGET_COPY_TO_MIDAS_SCALA_DIRS := \
	$(addprefix $(chipyard_dir)/generators/firechip/,bridgeinterfaces goldengateimplementations)

# this rule always is run, but may not update the timestamp of the targets (depending on what the Chipyard make does).
# if that is the case (Chipyard make doesn't update it's outputs), then downstream rules *should* be skipped.
# all other chipyard collateral is located in chipyard's generated sources area.
$(FIRRTL_FILE) $(ANNO_FILE) &: SHELL := /usr/bin/env bash # needed for running source in recipe
$(FIRRTL_FILE) $(ANNO_FILE) &: firesim_target_symlink_hook
	@mkdir -p $(@D)
	source $(chipyard_dir)/env.sh && \
		make -C $(CHIPYARD_STAGING_DIR) \
			SBT_PROJECT=$(TARGET_SBT_PROJECT) \
			MODEL=$(DESIGN) \
			MODEL_PACKAGE=$(DESIGN_PACKAGE) \
			VLOG_MODEL=$(DESIGN) \
			CONFIG=$(TARGET_CONFIG) \
			CONFIG_PACKAGE=$(TARGET_CONFIG_PACKAGE) \
			GENERATOR_PACKAGE=chipyard \
			EXTRA_CHISEL_OPTIONS=--emit-legacy-sfc \
			TB=unused \
			TOP=unused
	# $(long_name) must be same as Chipyard
	ln -sf $(CHIPYARD_STAGING_DIR)/generated-src/$(long_name)/$(long_name).sfc.fir $(FIRRTL_FILE)
	ln -sf $(CHIPYARD_STAGING_DIR)/generated-src/$(long_name)/$(long_name).anno.json $(ANNO_FILE)
	# .d needed to run metasim CI tests
	ln -sf $(CHIPYARD_STAGING_DIR)/generated-src/$(long_name)/$(long_name).d $(GENERATED_DIR)/$(long_name).d

#######################################
# Setup Extra Verilator Compile Flags #
#######################################

## default flags added for cva6
CVA6_VERILATOR_FLAGS = \
	--unroll-count 256 \
	-Werror-PINMISSING \
	-Werror-IMPLICIT \
	-Wno-fatal \
	-Wno-PINCONNECTEMPTY \
	-Wno-ASSIGNDLY \
	-Wno-DECLFILENAME \
	-Wno-UNUSED \
	-Wno-UNOPTFLAT \
	-Wno-BLKANDNBLK \
	-Wno-style \
	-Wall

# normal flags used for midas builds (that are incompatible with cva6)
DEFAULT_MIDAS_VERILATOR_FLAGS = \
	--assert

# AJG: this must be evaluated after verilog generation to work (hence the =)
EXTRA_VERILATOR_FLAGS = \
	$(shell if ! grep -iq "module.*cva6" $(simulator_verilog); then echo "$(DEFAULT_MIDAS_VERILATOR_FLAGS)"; else echo "$(CVA6_VERILATOR_FLAGS)"; fi)
