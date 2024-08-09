# See LICENSE for license details.

##########################
# Driver Sources & Flags #
##########################

driver_dir = $(firesim_base_dir)/src/main/cc

firesim_lib_dir = $(chipyard_dir)/generators/firechip/src/main/cc
testchipip_csrc_dir = $(chipyard_dir)/generators/testchipip/src/main/resources/testchipip/csrc

DRIVER_H = $(shell find $(driver_dir) -name "*.h")

DRIVER_CC := \
		$(driver_dir)/bridges/BridgeHarness.cc \
		$(driver_dir)/bridges/$(DESIGN).cc \
		$(testchipip_csrc_dir)/testchip_tsi.cc \
		$(testchipip_csrc_dir)/testchip_dtm.cc \
		$(testchipip_csrc_dir)/testchip_htif.cc \
		$(wildcard $(addprefix $(firesim_lib_dir)/, \
			bridges/uart.cc \
			bridges/tsibridge.cc \
			bridges/dmibridge.cc \
			bridges/blockdev.cc \
			bridges/tracerv.cc \
			fesvr/firesim_tsi.cc \
			fesvr/firesim_dtm.cc \
			$(addsuffix .cc, fesvr/* bridges/tracerv/*) \
		))

TARGET_CXX_FLAGS := \
		-isystem $(testchipip_csrc_dir) \
		-I$(RISCV)/include \
		-I$(firesim_lib_dir) \
		-I$(driver_dir)/midasexamples \
		-I$(driver_dir) \
		-I$(driver_dir)/bridges \
		-g

TARGET_LD_FLAGS := \
		-L$(RISCV)/lib \
		-lfesvr \
		-l:libdwarf.so -l:libelf.so
