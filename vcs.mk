WAVEFORM_FLAG=+vcdplusfile=$(sim_out_name).vpd

CLOCK_PERIOD ?= 1.0
RESET_DELAY ?= 777.7

VCS_CC_OPTS = \
	-CC "-I$(RISCV)/include" \
	-CC "-I$(dramsim_dir)" \
	-CC "-std=c++11" \
	-CC "$(EXTRA_SIM_CC_FLAGS)"

VCS_NONCC_OPTS = \
	$(dramsim_lib) \
	$(RISCV)/lib/libfesvr.a \
	+lint=all,noVCDE,noONGS,noUI \
	-error=PCWM-L \
	-error=noZMMCM \
	-timescale=1ns/10ps \
	-quiet \
	-q \
	+rad \
	+v2k \
	+vcs+lic+wait \
	+vc+list \
	-f $(sim_common_files) \
	-sverilog +systemverilogext+.sv+.svi+.svh+.svt -assert svaext +libext+.sv \
	+v2k +verilog2001ext+.v95+.vt+.vp +libext+.v \
	-debug_pp \
	+incdir+$(build_dir) \
	$(sim_vsrcs) \
	+libext+.v

VCS_DEFINE_OPTS = \
	+define+VCS \
	+define+CLOCK_PERIOD=$(CLOCK_PERIOD) \
	+define+RESET_DELAY=$(RESET_DELAY) \
	+define+PRINTF_COND=$(TB).printf_cond \
	+define+STOP_COND=!$(TB).reset \
	+define+RANDOMIZE_MEM_INIT \
	+define+RANDOMIZE_REG_INIT \
	+define+RANDOMIZE_GARBAGE_ASSIGN \
	+define+RANDOMIZE_INVALID_ASSIGN
