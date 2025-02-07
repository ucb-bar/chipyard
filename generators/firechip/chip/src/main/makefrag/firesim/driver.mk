# See LICENSE for license details.

# DOC include start: Bridge Build System Changes
##########################
# Driver Sources & Flags #
##########################

ifeq (,$(wildcard $(RISCV)/lib/libriscv.so))
$(warning libriscv not found)
LRISCV=
else
LRISCV=-lriscv
endif

firechip_lib_dir = $(chipyard_dir)/generators/firechip/chip/src/main/cc
firechip_bridgestubs_lib_dir = $(chipyard_dir)/generators/firechip/bridgestubs/src/main/cc
testchipip_csrc_dir = $(chipyard_dir)/generators/testchipip/src/main/resources/testchipip/csrc

# DRIVER_H only used to update recipe pre-reqs (ok to track more files)

# fesvr and related srcs
DRIVER_H += \
		$(shell find $(testchipip_csrc_dir) -name "*.h") \
		$(shell find $(firechip_bridgestubs_lib_dir)/fesvr -name "*.h")
DRIVER_CC += \
		$(testchipip_csrc_dir)/cospike_impl.cc \
		$(testchipip_csrc_dir)/testchip_tsi.cc \
		$(testchipip_csrc_dir)/testchip_dtm.cc \
		$(testchipip_csrc_dir)/testchip_htif.cc \
		$(firechip_bridgestubs_lib_dir)/fesvr/firesim_tsi.cc \
		$(firechip_bridgestubs_lib_dir)/fesvr/firesim_dtm.cc \
		$(RISCV)/lib/libfesvr.a
# Disable missing override warning for testchipip.
TARGET_CXX_FLAGS += \
		-isystem $(testchipip_csrc_dir) \
		-isystem $(RISCV)/include \
		-Wno-inconsistent-missing-override
TARGET_LD_FLAGS += \
		-L$(RISCV)/lib \
		-Wl,-rpath,$(RISCV)/lib \
		$(LRISCV)

# top-level sources
DRIVER_CC += $(addprefix $(firechip_lib_dir)/firesim/, $(addsuffix .cc, firesim_top))
TARGET_CXX_FLAGS += -I$(firechip_bridgestubs_lib_dir)/bridge/test

# bridge sources
DRIVER_H += $(shell find $(firechip_bridgestubs_lib_dir) -name "*.h")
DRIVER_CC += \
		$(wildcard \
			$(addprefix \
				$(firechip_bridgestubs_lib_dir)/, \
				$(addsuffix .cc,bridges/* bridges/tracerv/* bridges/cospike/*) \
			) \
		)
TARGET_CXX_FLAGS += \
		-I$(firechip_bridgestubs_lib_dir) \
		-I$(firechip_bridgestubs_lib_dir)/bridge \
		-I$(firechip_bridgestubs_lib_dir)/bridge/tracerv \
		-I$(firechip_bridgestubs_lib_dir)/bridge/cospike
TARGET_LD_FLAGS += \
	-l:libdwarf.so -l:libelf.so \
	-lz \

# other
TARGET_CXX_FLAGS += \
		-I$(GENERATED_DIR) \
		-g

# DOC include end: Bridge Build System Changes
