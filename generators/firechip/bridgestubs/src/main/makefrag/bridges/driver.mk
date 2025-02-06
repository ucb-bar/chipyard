# See LICENSE for license details.

##########################
# Driver Sources & Flags #
##########################

firechip_lib_dir = $(chipyard_dir)/generators/firechip/bridgestubs/src/main/cc
testchipip_csrc_dir = $(chipyard_dir)/generators/testchipip/src/main/resources/testchipip/csrc

# DRIVER_H only used to update recipe pre-reqs (ok to track more files)

# fesvr and related srcs
DRIVER_H += \
		$(shell find $(testchipip_csrc_dir) -name "*.h") \
		$(shell find $(firechip_lib_dir)/fesvr -name "*.h")
DRIVER_CC += \
		$(testchipip_csrc_dir)/testchip_tsi.cc \
		$(testchipip_csrc_dir)/testchip_htif.cc \
		$(firechip_lib_dir)/fesvr/firesim_tsi.cc \
		$(RISCV)/lib/libfesvr.a
# Disable missing override warning for testchipip.
TARGET_CXX_FLAGS += \
		-isystem $(testchipip_csrc_dir) \
		-isystem $(RISCV)/include \
		-Wno-inconsistent-missing-override
TARGET_LD_FLAGS += \
		-L$(RISCV)/lib \
		-Wl,-rpath,$(RISCV)/lib

# top-level testing sources
DRIVER_H += $(shell find $(firechip_lib_dir)/bridges/test -name "*.h")
DRIVER_CC += \
		$(wildcard $(addprefix $(firechip_lib_dir)/, \
			bridges/test/BridgeHarness.cc \
			bridges/test/$(DESIGN).cc \
		))
TARGET_CXX_FLAGS += -I$(firechip_lib_dir)/bridge/test

# bridge sources
# exclude the following types of files for unit testing
EXCLUDE_LIST := cospike dmibridge groundtest simplenic tsibridge
DRIVER_H += $(shell find $(firechip_lib_dir) -name "*.h")
DRIVER_CC += \
		$(filter-out \
			$(addprefix $(firechip_lib_dir)/bridges/,$(addsuffix .cc,$(EXCLUDE_LIST))), \
			$(wildcard \
				$(addprefix \
					$(firechip_lib_dir)/, \
					$(addsuffix .cc,bridges/* bridges/tracerv/*) \
				) \
			) \
		)
TARGET_CXX_FLAGS += \
		-I$(firechip_lib_dir) \
		-I$(firechip_lib_dir)/bridge \
		-I$(firechip_lib_dir)/bridge/tracerv
TARGET_LD_FLAGS += -l:libdwarf.so -l:libelf.so

# other
TARGET_CXX_FLAGS += \
		-g
