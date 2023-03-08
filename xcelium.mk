HELP_COMPILATION_VARIABLES += \
"   USE_VPD                = set to '1' to build VCS simulator to emit VPD instead of FSDB."

HELP_SIMULATION_VARIABLES += \
"   USE_VPD                = set to '1' to run VCS simulator emitting VPD instead of FSDB."

ifndef USE_VPD
WAVEFORM_FLAG=+fsdbfile=$(sim_out_name).fsdb
else
WAVEFORM_FLAG=+vcdplusfile=$(sim_out_name).vpd
endif

# If ntb_random_seed unspecified, vcs uses 1 as constant seed.
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

VCS_CXXFLAGS = $(addprefix $(XC_CXX_PREFIX), $(SIM_CXXFLAGS))
VCS_LDFLAGS = $(addprefix $(XC_LD_PREFIX), $(filter-out $(REMOVE_RPATH), $(SIM_LDFLAGS)))

# vcs requires LDFLAGS to not include library names (i.e. -l needs to be separate)
VCS_CC_OPTS = \
	$(VCS_CXXFLAGS) \
	$(VCS_LDFLAGS) \
	-enable_rpath

#-LDFLAGS "$(filter-out -l%,$(VCS_LDFLAGS))" \
#	$(filter -l%,$(VCS_LDFLAGS))

VCS_NONCC_OPTS = \
	-fast_recompilation \
	-top $(TB) \
	-sv \
	-ALLOWREDEFINITION \
	-timescale 1ns/10ps \
	-define INTCNOPWR \
	-define INTC_NO_PWR_PINS \
	-define INTC_EMULATION \
	-f $(sim_common_files) \
	-logfile xrun_elab.log \
	-glsperf \
	-genafile access.txt \
	-notimingchecks \
	-delay_mode zero

PREPROC_DEFINES = \
	-define VCS \
	-define CLOCK_PERIOD=$(CLOCK_PERIOD) \
	-define RESET_DELAY=$(RESET_DELAY) \
	-define PRINTF_COND=$(TB).printf_cond \
	-define STOP_COND=!$(TB).reset \
	-define MODEL=$(MODEL) \
	-define RANDOMIZE_MEM_INIT \
	-define RANDOMIZE_REG_INIT \
	-define RANDOMIZE_GARBAGE_ASSIGN \
	-define RANDOMIZE_INVALID_ASSIGN

ifndef USE_VPD
PREPROC_DEFINES += +define+FSDB
endif
