#----------------------------------------------------------------------------------------
# common gcc configuration/optimization
#----------------------------------------------------------------------------------------
SIM_OPT_CXXFLAGS := -O3
LRISCV=-lriscv

export USE_CHISEL6=1

SIM_CXXFLAGS = \
	$(CXXFLAGS) \
	$(SIM_OPT_CXXFLAGS) \
	-std=c++17 \
	-I$(RISCV)/include \
	-I$(GEN_COLLATERAL_DIR) \
	$(EXTRA_SIM_CXXFLAGS)  \
	-I$(dramsim_dir) \
	-I$(dramsim_dir)/src \
	-I/scratch/achaurasia/chipyard/tools/DRAMSim3/ext/fmt/include/fmt \
	-I/scratch/achaurasia/chipyard/tools/DRAMSim3/ext/headers \

SIM_LDFLAGS = \
	$(LDFLAGS) \
	-L$(RISCV)/lib \
	-Wl,-rpath,$(RISCV)/lib \
	-L$(sim_dir) \
	-L$(dramsim_dir) \
	-L$(dramsim_dir)/build \
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
