# See LICENSE for license details.

################################################################
# SW RTL Simulation Args -- for MIDAS- & FPGA-level Simulation #
################################################################
TIMEOUT_CYCLES = 100000000

NET_SLOT ?= 0
NET_LINK_LATENCY ?= 6405
NET_BW ?= 100
NET_SHMEMPORTNAME ?= $(shell printf '%0100d' $(NET_SLOT))
NET_LOOPBACK ?= +nic-loopback0
NET_MACADDR ?= $(shell printf '00:00:00:00:00:%02x' $$(($(NET_SLOT)+2)))
nic_args = +shmemportname0=$(NET_SHMEMPORTNAME) +macaddr0=$(NET_MACADDR) \
	+niclog0=niclog$(NET_SLOT) +linklatency0=$(NET_LINK_LATENCY) \
	+netbw0=$(NET_BW) +netburst0=8 $(NET_LOOPBACK)
tracer_args = +tracefile=TRACEFILE
blkdev_args = +blkdev-in-mem0=128 +blkdev-log0=blkdev-log$(NET_SLOT)
autocounter_args = +autocounter-readrate=1000 +autocounter-filename-base=AUTOCOUNTERFILE
# Neglecting this +arg will make the simulator use the same step size as on the
# FPGA. This will make ML simulation more closely match results seen on the
# FPGA at the expense of dramatically increased target runtime
serial_args = +fesvr-step-size=128
#serial_args =

COMMON_SIM_ARGS := $(COMMON_SIM_ARGS) $(serial_args) $(nic_args) $(tracer_args) $(blkdev_args) $(autocounter_args)

# Arguments used only at a particular simulation abstraction
MIDAS_LEVEL_SIM_ARGS ?= +max-cycles=$(TIMEOUT_CYCLES)
FPGA_LEVEL_SIM_ARGS ?=

################################
# Verilator/VCS/XSIM execution #
################################

verilator = $(GENERATED_DIR)/V$(DESIGN)
verilator_debug = $(GENERATED_DIR)/V$(DESIGN)-debug
verilator_args =
vcs = $(GENERATED_DIR)/$(DESIGN)
vcs_debug = $(GENERATED_DIR)/$(DESIGN)-debug
vcs_args = +vcs+initreg+0 +vcs+initmem+0
xsim = $(GENERATED_DIR)/$(DESIGN)-$(PLATFORM)
xcelium = $(GENERATED_DIR)/X$(DESIGN)
sim_binary_basename := $(basename $(notdir $(SIM_BINARY)))

run-verilator: $(verilator)
	cd $(dir $<) && \
	$(verilator) +permissive $(verilator_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) +permissive-off $(abspath $(SIM_BINARY)) </dev/null \
	$(disasm) $(firesim_base_dir)/$(sim_binary_basename).out

run-verilator-debug: $(verilator_debug)
	cd $(dir $<) && \
	$(verilator_debug) +permissive $(verilator_args) +waveformfile=$(sim_binary_basename).vcd $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) +permissive-off $(abspath $(SIM_BINARY)) </dev/null \
	$(disasm) $(firesim_base_dir)/$(sim_binary_basename).out

run-vcs: $(vcs)
	cd $(dir $<) && \
	$(vcs) +permissive $(vcs_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) +permissive-off $(abspath $(SIM_BINARY)) </dev/null \
	$(disasm) $(firesim_base_dir)/$(sim_binary_basename).out

run-vcs-debug: $(vcs_debug)
	cd $(dir $<) && \
	$(vcs_debug) +permissive $(vcs_args) +fsdbfile=$(sim_binary_basename).fsdb $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) +permissive-off $(abspath $(SIM_BINARY)) </dev/null \
	$(disasm) $(firesim_base_dir)/$(sim_binary_basename).out

run-xcelium: $(xcelium)
	cd $(dir $<) && \
	$(xcelium) +permissive $(vcs_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) +permissive-off $(abspath $(SIM_BINARY)) </dev/null \
	$(disasm) $(firesim_base_dir)/$(sim_binary_basename).out

run-xsim: $(xsim)
	cd $(dir $<) && ./$(notdir $<)  +permissive $(COMMON_SIM_ARGS) $(FPGA_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) +permissive-off $(abspath $(SIM_BINARY)) </dev/null \
	$(disasm) $(firesim_base_dir)/$(sim_binary_basename).out

.PHONY: run-verilator run-verilator-debug run-vcs run-vcs-debug run-xcelium run-xsim

############################################
# Midas-Level Simulation Execution Recipes #
############################################
# The desired RTL simulator. supported options: {vcs, verilator}
EMUL ?= verilator

# Firechip Tests
fc_test_dir = $(chipyard_dir)/tests
fc_test_srcs = $(wildcard $(fc_test_dir)/*.c)
fc_test_hdrs = $(wildcard $(fc_test_dir)/*.h)

$(fc_test_dir)/%.riscv: $(fc_test_srcs) $(fc_test_hdrs) $(fc_test_dir)/Makefile
	make -C $(fc_test_dir)

ifneq ($(filter run% %.run %.out %.vpd %.vcd,$(MAKECMDGOALS)),)
output_dir := $(OUTPUT_DIR)
-include $(GENERATED_DIR)/$(long_name).d
endif


disasm := 2>
which_disasm := $(shell which spike-dasm 2> /dev/null)
ifneq ($(which_disasm),)
        disasm := 3>&1 1>&2 2>&3 | $(which_disasm) $(DISASM_EXTENSION) >
endif

# Some of the generated suites use specific plus args, that are prefixed with
# the binary name. These are captured with $($*_ARGS)
$(OUTPUT_DIR)/%.run: $(OUTPUT_DIR)/% $(EMUL)
	cd $(dir $($(EMUL))) && \
	./$(notdir $($(EMUL))) $< $($*_ARGS) $($(EMUL)_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) \
	2> /dev/null 2> $@ && [ $$PIPESTATUS -eq 0 ]

$(OUTPUT_DIR)/%.out: $(OUTPUT_DIR)/% $(EMUL)
	cd $(dir $($(EMUL))) && \
	./$(notdir $($(EMUL))) $< $($*_ARGS) $($(EMUL)_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) \
	$(disasm) $@ && [ $$PIPESTATUS -eq 0 ]

$(OUTPUT_DIR)/%.vpd: $(OUTPUT_DIR)/% $(EMUL)-debug
	cd $(dir $($(EMUL)_debug)) && \
	./$(notdir $($(EMUL)_debug)) $< +vcdplusfile=$@ $($*_ARGS) $($(EMUL)_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) \
	$(disasm) $(patsubst %.vpd,%.out,$@) && [ $$PIPESTATUS -eq 0 ]

$(OUTPUT_DIR)/%.fsdb: $(OUTPUT_DIR)/% $(EMUL)-debug
	cd $(dir $($(EMUL)_debug)) && \
	./$(notdir $($(EMUL)_debug)) $< +fsdbfile=$@ $($*_ARGS) $($(EMUL)_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) \
	$(disasm) $(patsubst %.fsdb,%.out,$@) && [ $$PIPESTATUS -eq 0 ]

.PRECIOUS: $(OUTPUT_DIR)/%.fsdb $(OUTPUT_DIR)/%.vpd $(OUTPUT_DIR)/%.out $(OUTPUT_DIR)/%.run

# TraceGen rules

$(OUTPUT_DIR)/tracegen.out: $($(EMUL))
	mkdir -p $(OUTPUT_DIR) && \
	cd $(dir $($(EMUL))) && \
	./$(notdir $($(EMUL))) $($(EMUL)_args) $(COMMON_SIM_ARGS) $(MIDAS_LEVEL_SIM_ARGS) $(EXTRA_SIM_ARGS) \
	2> /dev/null 2> $@ && [ $$PIPESTATUS -eq 0 ]

$(OUTPUT_DIR)/tracegen.result: $(OUTPUT_DIR)/tracegen.out $(AXE)
	$(chipyard_dir)/scripts/check-tracegen.sh $< > $@

fsim-tracegen: $(OUTPUT_DIR)/tracegen.result

.PHONY: fsim-tracegen
