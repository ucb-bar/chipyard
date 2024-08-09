# See LICENSE for license details.

# DOC include start: Bridge Build System Changes
##########################
# Driver Sources & Flags #
##########################

TESTCHIPIP_CSRC_DIR = $(chipyard_dir)/generators/testchipip/src/main/resources/testchipip/csrc

ifeq (,$(wildcard $(RISCV)/lib/libriscv.so))
$(warning libriscv not found)
LRISCV=
else
LRISCV=-lriscv
endif

firesim_lib_dir = $(firesim_base_dir)/firesim-lib/src/main/cc
driver_dir = $(firesim_base_dir)/src/main/cc
DRIVER_H = \
	$(shell find $(driver_dir) -name "*.h") \
	$(shell find $(firesim_lib_dir) -name "*.h") \
	$(TESTCHIPIP_CSRC_DIR)/cospike_impl.h \
	$(TESTCHIPIP_CSRC_DIR)/testchip_tsi.h \
	$(TESTCHIPIP_CSRC_DIR)/testchip_dtm.h \
	$(TESTCHIPIP_CSRC_DIR)/testchip_htif.h

DRIVER_CC = \
	$(addprefix $(driver_dir)/firesim/, $(addsuffix .cc, firesim_top)) \
	$(wildcard $(addprefix $(firesim_lib_dir)/, $(addsuffix .cc, bridges/* fesvr/* bridges/tracerv/* bridges/cospike/*)))  \
	$(RISCV)/lib/libfesvr.a \
	$(TESTCHIPIP_CSRC_DIR)/cospike_impl.cc \
	$(TESTCHIPIP_CSRC_DIR)/testchip_tsi.cc \
	$(TESTCHIPIP_CSRC_DIR)/testchip_dtm.cc \
	$(TESTCHIPIP_CSRC_DIR)/testchip_htif.cc

# Disable missing override warning for testchipip.
TARGET_CXX_FLAGS += -g \
	-isystem $(RISCV)/include \
	-isystem $(TESTCHIPIP_CSRC_DIR) \
	-I$(driver_dir)/firesim \
	-I$(firesim_lib_dir) \
	-I$(GENERATED_DIR) \
	-Wno-inconsistent-missing-override
TARGET_LD_FLAGS += \
	-L$(CONDA_PREFIX)/lib \
	-l:libdwarf.so \
	-l:libelf.so \
	-lz \
	-L$(RISCV)/lib \
	-Wl,-rpath,$(RISCV)/lib \
	$(LRISCV)
# DOC include end: Bridge Build System Changes
