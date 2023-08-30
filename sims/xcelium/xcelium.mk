
get_waveform_flag=+vcdfile=$(1).vcd

# If ntb_random_seed unspecified, xcelium uses 1 as constant seed.
# Set ntb_random_seed_automatic to actually get a random seed
ifdef RANDOM_SEED
SEED_FLAG=+ntb_random_seed=$(RANDOM_SEED)
else
SEED_FLAG=+ntb_random_seed_automatic
endif

CLOCK_PERIOD ?= 1.0
RESET_DELAY ?= 777.7

#----------------------------------------------------------------------------------------
# gcc configuration/optimization
#----------------------------------------------------------------------------------------
include $(base_dir)/sims/common-sim-flags.mk


XC_CXX_PREFIX=-Wcxx,
XC_LD_PREFIX=-Wld,

REMOVE_RPATH=-Wl,-rpath%

XCELIUM_CXXFLAGS = $(addprefix $(XC_CXX_PREFIX), $(SIM_CXXFLAGS))
XCELIUM_LDFLAGS = $(addprefix $(XC_LD_PREFIX), $(filter-out $(REMOVE_RPATH), $(SIM_LDFLAGS)))

XCELIUM_COMMON_ARGS = \
	-64bit \
	-xmlibdirname $(sim_workdir) \
	-l /dev/null \
	-log_xmsc_run /dev/null

XCELIUM_CC_OPTS = \
	$(XCELIUM_CXXFLAGS) \
	$(XCELIUM_LDFLAGS) \
	-enable_rpath

XCELIUM_NONCC_OPTS = \
	-fast_recompilation \
	-top $(TB) \
	-sv \
	-ALLOWREDEFINITION \
	-timescale 1ns/10ps \
	-define INTCNOPWR \
	-define INTC_NO_PWR_PINS \
	-define INTC_EMULATION \
	-f $(sim_common_files) \
	-glsperf \
	-notimingchecks \
	-delay_mode zero

PREPROC_DEFINES = \
	-define XCELIUM \
	-define CLOCK_PERIOD=$(CLOCK_PERIOD) \
	-define RESET_DELAY=$(RESET_DELAY) \
	-define PRINTF_COND=$(TB).printf_cond \
	-define STOP_COND=!$(TB).reset \
	-define MODEL=$(MODEL) \
	-define RANDOMIZE_MEM_INIT \
	-define RANDOMIZE_REG_INIT \
	-define RANDOMIZE_GARBAGE_ASSIGN \
	-define RANDOMIZE_INVALID_ASSIGN
