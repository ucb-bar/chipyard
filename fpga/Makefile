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
MODEL_PACKAGE     := sifive.freedom.everywhere.e300artydevkit
CONFIG            := E300ArtyDevKitConfig
CONFIG_PACKAGE    := sifive.freedom.everywhere.e300artydevkit
GENERATOR_PACKAGE := chipyard
TB                := none # unused
TOP               := E300ArtyDevKitPlatform

# setup the board to use
BOARD ?= arty

#########################################################################################
# misc. directories
#########################################################################################
bootrom_dir := $(base_dir)/fpga/bootrom/xip
fpga_common_script_dir := $(FPGA_DIR)/common/tcl
fpga_dir := $(base_dir)/fpga/fpga-shells/xilinx

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
	$(fpga_dir)/common/vsrc/PowerOnResetFPGAOnly.v \
	$(build_dir)/$(long_name).rom.v

#########################################################################################
# build rom for the fpga
#########################################################################################
# needed for bootrom makefile
export BUILD_DIR=$(build_dir)
export ROCKETCHIP_DIR
export LONG_NAME=$(long_name)
export ROMCONF=$(build_dir)/$(long_name).rom.conf

romgen := $(build_dir)/$(long_name).rom.v
$(romgen): $(sim_vsrcs)
ifneq ($(bootrom_dir),"")
	$(MAKE) -C $(bootrom_dir) romgen
	mv $(build_dir)/rom.v $@
endif

.PHONY: romgen
romgen: $(romgen)

#########################################################################################
# vivado rules
#########################################################################################
# combine all sources into single .F
f := $(build_dir)/$(long_name).vsrcs.F
$(f): $(sim_common_files) $(all_vsrcs)
	$(foreach file,$(all_vsrcs),echo "$(file)" >> $@;)
	cat $(sim_common_files) >> $@

bit := $(build_dir)/obj/$(MODEL).bit
$(bit): $(romgen) $(f)
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(fpga_common_script_dir)/vivado.tcl \
		-tclargs \
		-top-module "$(MODEL)" \
		-F "$(f)" \
		-ip-vivado-tcls "$(shell find '$(build_dir)' -name '*.vivado.tcl')" \
		-board "$(BOARD)"

.PHONY: bit
bit: $(bit)

# Build .mcs
mcs := $(build_dir)/obj/$(MODEL).mcs
$(mcs): $(bit)
	cd $(build_dir); vivado -nojournal -mode batch -source $(fpga_common_script_dir)/write_cfgmem.tcl -tclargs $(BOARD) $@ $<

.PHONY: mcs
mcs: $(mcs)

#########################################################################################
# mircosemi rules
#########################################################################################
# Build Libero project
prjx := $(build_dir)/libero/$(MODEL).prjx
$(prjx): $(verilog)
	cd $(build_dir); libero SCRIPT:$(fpga_common_script_dir)/libero.tcl SCRIPT_ARGS:"$(build_dir) $(MODEL) $(PROJECT) $(CONFIG) $(BOARD)"

.PHONY: prjx
prjx: $(prjx)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir)
ifneq ($(bootrom_dir),"")
	$(MAKE) -C $(bootrom_dir) clean
endif
