#----------------------------------------------------------------------------------------
# common gcc configuration/optimization
#----------------------------------------------------------------------------------------
SIM_OPT_CXXFLAGS := -O3

# Workaround: esp-isa-sim doesn't install libriscv,
# so don't link with libriscv if it doesn't exist
# potentially breaks some configs

ifeq (,$(wildcard $(RISCV)/lib/libriscv.so))
$(warning libriscv not found)
LRISCV=
else
LRISCV=-lriscv
endif

SIM_CXXFLAGS = \
	$(CXXFLAGS) \
	$(SIM_OPT_CXXFLAGS) \
	-std=c++17 \
	-I$(RISCV)/include \
	-I$(dramsim_dir) \
	-I$(OUT_DIR) \
	$(EXTRA_SIM_CXXFLAGS)

SIM_LDFLAGS = \
	$(LDFLAGS) \
	-L$(RISCV)/lib \
	-Wl,-rpath,$(RISCV)/lib \
	-L$(sim_dir) \
	-L$(dramsim_dir) \
	$(LRISCV) \
	-lfesvr \
	-ldramsim \
	$(EXTRA_SIM_LDFLAGS)

SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/EICG_wrapper.v
