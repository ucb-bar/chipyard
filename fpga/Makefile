#########################################################################################
# fpga prototype makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ..)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

export SUB_PROJECT=fpga
export SBT_PROJECT=freedomPlatforms
export MODEL=E300ArtyDevKitFPGAChip
export VLOG_MODEL=E300ArtyDevKitFPGAChip
export MODEL_PACKAGE=sifive.freedom.everywhere.e300artydevkit
export CONFIG=E300ArtyDevKitConfig
export CONFIG_PACKAGE=sifive.freedom.everywhere.e300artydevkit
export GENERATOR_PACKAGE=chipyard
export TB=none
export TOP=E300ArtyDevKitPlatform
export BOARD=arty

export bootrom_dir := $(base_dir)/fpga/bootrom/xip
fpga_dir=$(base_dir)/fpga/fpga-shells/xilinx

sim_name = verilator # unused

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# copy from other directory
#########################################################################################
romgen := $(build_dir)/$(CONFIG_PROJECT).$(CONFIG).rom.v
$(romgen): $(verilog)
ifneq ($(bootrom_dir),"")
	$(MAKE) -C $(bootrom_dir) romgen
	mv $(build_dir)/rom.v $@
endif

.PHONY: romgen
romgen: $(romgen)

f := $(build_dir)/$(CONFIG_PROJECT).$(CONFIG).vsrcs.F
$(f):
	echo $(VSRCS) > $@

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


# Build .mcs
mcs := $(build_dir)/obj/$(MODEL).mcs
$(mcs): $(bit)
	cd $(build_dir); vivado -nojournal -mode batch -source $(fpga_common_script_dir)/write_cfgmem.tcl -tclargs $(BOARD) $@ $<

.PHONY: mcs
mcs: $(mcs)

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
	$(MAKE) -C $(FPGA_DIR) clean
