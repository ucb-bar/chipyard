HELP_COMPILATION_VARIABLES += \
"   USE_FSDB               = set to '1' to build VCS simulator to emit FSDB instead of VPD."

HELP_SIMULATION_VARIABLES += \
"   USE_FSDB               = set to '1' to run VCS simulator emitting FSDB instead of VPD."

ifdef USE_FSDB
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

VCS_CXXFLAGS = $(SIM_CXXFLAGS)
VCS_LDFLAGS = $(SIM_LDFLAGS)

# vcs requires LDFLAGS to not include library names (i.e. -l needs to be separate)
VCS_CC_OPTS = \
	-CFLAGS "$(VCS_CXXFLAGS)" \
	-LDFLAGS "$(filter-out -l%,$(VCS_LDFLAGS))" \
	$(filter -l%,$(VCS_LDFLAGS))

VCS_NONCC_OPTS = \
	-notice \
	-line \
	+lint=all,noVCDE,noONGS,noUI \
	-error=PCWM-L \
	-error=noZMMCM \
	-timescale=1ns/10ps \
	-quiet \
	-q \
	+rad \
	+vcs+lic+wait \
	+vc+list \
	-f $(sim_common_files) \
	-sverilog +systemverilogext+.sv+.svi+.svh+.svt -assert svaext +libext+.sv \
	+v2k +verilog2001ext+.v95+.vt+.vp +libext+.v \
	-debug_pp \
	+incdir+$(build_dir) \
	$(sim_vsrcs)

PREPROC_DEFINES = \
	+define+VCS \
	+define+CLOCK_PERIOD=$(CLOCK_PERIOD) \
	+define+RESET_DELAY=$(RESET_DELAY) \
	+define+PRINTF_COND=$(TB).printf_cond \
	+define+STOP_COND=!$(TB).reset \
	+define+MODEL=$(MODEL) \
	+define+RANDOMIZE_MEM_INIT \
	+define+RANDOMIZE_REG_INIT \
	+define+RANDOMIZE_GARBAGE_ASSIGN \
	+define+RANDOMIZE_INVALID_ASSIGN

ifdef USE_FSDB
PREPROC_DEFINES += +define+FSDB
endif
