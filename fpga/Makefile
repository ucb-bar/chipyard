#########################################################################################
# fpga prototype makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ..)
sim_dir=$(abspath .)

# do not generate simulation files
sim_name := none

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

# default variables to build the arty example
SUB_PROJECT       := fpga
SBT_PROJECT       := freedomPlatforms
MODEL             := E300ArtyDevKitFPGAChip
VLOG_MODEL        := E300ArtyDevKitFPGAChip
MODEL_PACKAGE     := chipyard.fpga
CONFIG            := E300ArtyDevKitConfig
CONFIG_PACKAGE    := chipyard.fpga
GENERATOR_PACKAGE := chipyard
TB                := none # unused
TOP               := E300ArtyDevKitPlatform

# setup the board to use
BOARD ?= arty

.PHONY: default
default: $(mcs)

#########################################################################################
# misc. directories
#########################################################################################
fpga_dir := $(base_dir)/fpga/fpga-shells/xilinx
fpga_common_script_dir := $(fpga_dir)/common/tcl

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# copy from other directory
#########################################################################################
all_vsrcs := \
	$(sim_vsrcs) \
	$(base_dir)/generators/sifive-blocks/vsrc/SRLatch.v \
	$(fpga_dir)/common/vsrc/PowerOnResetFPGAOnly.v

#########################################################################################
# vivado rules
#########################################################################################
# combine all sources into single .f
synth_list_f := $(build_dir)/$(long_name).vsrcs.f
$(synth_list_f): $(sim_common_files) $(all_vsrcs)
	$(foreach file,$(all_vsrcs),echo "$(file)" >> $@;)
	cat $(sim_common_files) >> $@

BIT_FILE := $(build_dir)/obj/$(MODEL).bit
$(BIT_FILE): $(synth_list_f)
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(fpga_common_script_dir)/vivado.tcl \
		-tclargs \
		-top-module "$(MODEL)" \
		-F "$(synth_list_f)" \
		-ip-vivado-tcls "$(shell find '$(build_dir)' -name '*.vivado.tcl')" \
		-board "$(BOARD)"

.PHONY: bit
bit: $(BIT_FILE)

# Build .mcs
MCS_FILE := $(build_dir)/obj/$(MODEL).mcs
$(MCS_FILE): $(BIT_FILE)
	cd $(build_dir); vivado -nojournal -mode batch -source $(fpga_common_script_dir)/write_cfgmem.tcl -tclargs $(BOARD) $@ $<

.PHONY: mcs
mcs: $(MCS_FILE)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir)
