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
	-I$(GEN_COLLATERAL_DIR) \
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

CLOCK_PERIOD ?= 1.0
RESET_DELAY ?= 777.7

SIM_PREPROC_DEFINES = \
	+define+CLOCK_PERIOD=$(CLOCK_PERIOD) \
	+define+RESET_DELAY=$(RESET_DELAY) \
	+define+PRINTF_COND=$(TB).printf_cond \
	+define+STOP_COND=!$(TB).reset \
	+define+MODEL=$(MODEL) \
	+define+RANDOMIZE_MEM_INIT \
	+define+RANDOMIZE_REG_INIT \
	+define+RANDOMIZE_GARBAGE_ASSIGN \
	+define+RANDOMIZE_INVALID_ASSIGN
