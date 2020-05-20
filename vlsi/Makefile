#########################################################################################
# vlsi makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ..)
vlsi_dir=$(abspath .)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# vlsi types and rules
#########################################################################################
sim_name           ?= vcs # needed for GenerateSimFiles, but is unused
tech_name          ?= asap7
tech_dir           ?= $(if $(filter $(tech_name), asap7), $(vlsi_dir)/hammer/src/hammer-vlsi/technology/$(tech_name), $(vlsi_dir)/hammer-$(tech_name)-plugin/$(tech_name))
SMEMS_COMP         ?= $(tech_dir)/sram-compiler.json
SMEMS_CACHE        ?= $(tech_dir)/sram-cache.json
SMEMS_HAMMER       ?= $(build_dir)/$(long_name).mems.hammer.json
ifeq ($(tech_name),asap7)
	MACROCOMPILER_MODE ?= --mode synflops
else
	MACROCOMPILER_MODE ?= -l $(SMEMS_CACHE) -hir $(SMEMS_HAMMER)
endif
ENV_YML            ?= $(vlsi_dir)/env.yml
INPUT_CONFS        ?= example.yml
HAMMER_EXEC        ?= ./example-vlsi
VLSI_TOP           ?= $(TOP)
VLSI_OBJ_DIR       ?= $(vlsi_dir)/build
ifneq ($(CUSTOM_VLOG), )
	OBJ_DIR        ?= $(VLSI_OBJ_DIR)/custom-$(VLSI_TOP)
else
	OBJ_DIR        ?= $(VLSI_OBJ_DIR)/$(long_name)-$(VLSI_TOP)
endif

#########################################################################################
# general rules
#########################################################################################
ALL_RTL = $(TOP_FILE) $(TOP_SMEMS_FILE)
extra_v_includes = $(build_dir)/EICG_wrapper.v $(vlsi_dir)/example.v
ifneq ($(CUSTOM_VLOG), )
	VLSI_RTL = $(CUSTOM_VLOG)
	VLSI_BB = /dev/null
else
	VLSI_RTL = $(ALL_RTL) $(extra_v_includes)
	VLSI_BB = $(sim_top_blackboxes)
endif

.PHONY: default verilog
default: all

all: drc lvs

verilog: $(ALL_RTL)

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# srams
#########################################################################################
SRAM_GENERATOR_CONF = $(OBJ_DIR)/sram_generator-input.yml
SRAM_CONF=$(OBJ_DIR)/sram_generator-output.json

## SRAM Generator
.PHONY: sram_generator srams
srams: sram_generator
sram_generator: $(SRAM_CONF)

# This should be built alongside $(TOP_SMEMS_FILE)
$(SMEMS_HAMMER): $(TOP_SMEMS_FILE)

$(SRAM_GENERATOR_CONF): $(SMEMS_HAMMER)
	mkdir -p $(dir $@)
	echo "vlsi.inputs.sram_parameters: '$(SMEMS_HAMMER)'" >> $@
	echo "vlsi.inputs.sram_parameters_meta: [\"transclude\", \"json2list\"]">> $@

$(SRAM_CONF): $(SRAM_GENERATOR_CONF)
	cd $(vlsi_dir) && $(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $(SRAM_GENERATOR_CONF), -p $(x)) --obj_dir $(build_dir) sram_generator
	cd $(vlsi_dir) && cp output.json $@

#########################################################################################
# simulation input configuration
#########################################################################################
include $(base_dir)/vcs.mk
SIM_CONF = $(OBJ_DIR)/sim-inputs.yml
SIM_DEBUG_CONF = $(OBJ_DIR)/sim-debug-inputs.yml
SIM_TIMING_CONF = $(OBJ_DIR)/sim-timing-inputs.yml

include $(vlsi_dir)/sim.mk
$(SIM_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $(HARNESS_FILE) $(HARNESS_SMEMS_FILE); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  input_files_meta: 'append'" >> $@
	echo "  timescale: '1ns/10ps'" >> $@
	echo "  options: [" >> $@
	echo "    '$(RISCV)/lib/libfesvr.a'," >> $@
	echo "    '+lint=all,noVCDE,noONGS,noUI'," >> $@
	echo "    '-error=PCWM-L'," >> $@
	echo "    '-quiet'," >> $@
	echo "    '-q'," >> $@
	echo "    '+rad'," >> $@
	echo "    '+v2k'," >> $@
	echo "    '+vcs+lic+wait'," >> $@
	echo "    '+vc+list'," >> $@
	echo "    '-f $(sim_common_files)'," >> $@
	echo "    '-sverilog'," >> $@
	echo "    '-debug_pp']" >> $@
	echo "  options_meta: 'append'" >> $@
	echo "  defines: [" >> $@
	echo "    'CLOCK_PERIOD=1.0'," >> $@
	echo "    'PRINTF_COND=$(TB).printf_cond'," >> $@
	echo "    'STOP_COND=!$(TB).reset'," >> $@
	echo "    'RANDOMIZE_MEM_INIT'," >> $@
	echo "    'RANDOMIZE_REG_INIT'," >> $@
	echo "    'RANDOMIZE_GARBAGE_ASSIGN'," >> $@
	echo "    'RANDOMIZE_INVALID_ASSIGN']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  compiler_opts: [" >> $@
	echo "    '-I$(RISCV)/include'," >> $@
	echo "    '-std=c++11']" >> $@
	echo "  compiler_opts_meta: 'append'" >> $@
	echo "  execution_flags_prepend: ['$(PERMISSIVE_ON)']" >> $@
	echo "  execution_flags_append: ['$(PERMISSIVE_OFF)']" >> $@
	echo "  execution_flags: [" >> $@
	echo "    '+max-cycles=$(timeout_cycles)'," >> $@
	for x in $(SIM_FLAGS); do \
	  echo '    "'$$x'",' >> $@; \
	done
	echo "  ]" >> $@
	echo "  execution_flags_meta: 'append'" >> $@
	echo "  benchmarks: ['$(BINARY)']" >> $@
	echo "  tb_dut: 'testHarness.top'" >> $@

$(SIM_DEBUG_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  defines: [" >> $@
	echo "    'DEBUG']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  execution_flags: [" >> $@
	for x in $(VERBOSE_FLAGS) $(WAVEFORM_FLAG); do \
	  echo '    "'$$x'",' >> $@; \
	done
	echo "  ]" >> $@
	echo "  execution_flags_meta: 'append'" >> $@
	echo "sim.outputs.waveforms: ['$(sim_out_name).vpd']" >> $@

$(SIM_TIMING_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  defines: [" >> $@
	echo "    'NTC']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  timing_annotated: 'true'" >> $@

POWER_CONF = $(OBJ_DIR)/power-inputs.yml
include $(vlsi_dir)/power.mk
LOWER_VLSI_TOP = $(shell echo $(VLSI_TOP) | tr A-Z a-z)
$(POWER_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	echo "power.inputs:" > $@
	echo "  tb_dut: 'testHarness/$(LOWER_VLSI_TOP)'" >> $@
	echo "  database: '$(OBJ_DIR)/par-rundir/$(VLSI_TOP)_FINAL'" >> $@
	echo "  saifs: [" >> $@
	echo "    '$(OBJ_DIR)/sim-par-rundir/$(notdir $(BINARY))/ucli.saif'" >> $@
	echo "  ]" >> $@
	echo "  waveforms: [" >> $@
	echo "    '$(OBJ_DIR)/sim-par-rundir/$(notdir $(BINARY))/$(sim_out_name).vcd'" >> $@
	echo "  ]" >> $@
	echo "  start_times: [" >> $@
	echo "    0" >> $@
	echo "  ]" >> $@
	echo "  end_times: [" >> $@
	echo "    15000" >> $@ #timeout_cycles * clock_period
	echo "  ]" >> $@

#########################################################################################
# synthesis input configuration
#########################################################################################
SYN_CONF = $(OBJ_DIR)/inputs.yml
GENERATED_CONFS = $(SYN_CONF)
ifeq ($(CUSTOM_VLOG), )
	GENERATED_CONFS += $(if $(filter $(tech_name), asap7), , $(SRAM_CONF))
endif

$(SYN_CONF): $(VLSI_RTL) $(VLSI_BB)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  input_files:" >> $@
	for x in $(VLSI_RTL); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  input_files_meta: 'append'" >> $@
	echo "synthesis.inputs:" >> $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $(VLSI_RTL) `cat $(VLSI_BB)`; do \
		echo '    - "'$$x'"' >> $@; \
	done

#########################################################################################
# AUTO BUILD FLOW
#########################################################################################

.PHONY: buildfile
buildfile: $(OBJ_DIR)/hammer.d
# Tip: Set HAMMER_D_DEPS to an empty string to avoid unnecessary RTL rebuilds
# TODO: make this dependency smarter so that we don't need this at all
HAMMER_D_DEPS ?= $(GENERATED_CONFS)
$(OBJ_DIR)/hammer.d: $(HAMMER_D_DEPS)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $(GENERATED_CONFS), -p $(x)) --obj_dir $(OBJ_DIR) build

-include $(OBJ_DIR)/hammer.d

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(VLSI_OBJ_DIR) hammer-vlsi*.log __pycache__ output.json $(GENERATED_CONFS) $(gen_dir) $(SIM_CONF) $(SIM_DEBUG_CONF)
