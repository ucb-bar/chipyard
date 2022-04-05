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
tech_dir           ?= $(if $(filter $(tech_name),sky130 asap7 nangate45),\
                        $(vlsi_dir)/hammer/src/hammer-vlsi/technology/$(tech_name), \
                        $(vlsi_dir)/hammer-$(tech_name)-plugin/$(tech_name))
SMEMS_COMP         ?= $(tech_dir)/sram-compiler.json
SMEMS_CACHE        ?= $(tech_dir)/sram-cache.json
SMEMS_HAMMER       ?= $(build_dir)/$(long_name).mems.hammer.json

ifdef USE_SRAM_COMPILER
	MACROCOMPILER_MODE ?= -l $(SMEMS_COMP) --use-compiler -hir $(SMEMS_HAMMER) --mode strict
else
	MACROCOMPILER_MODE ?= -l $(SMEMS_CACHE) -hir $(SMEMS_HAMMER) --mode strict
endif	

ENV_YML            ?= $(vlsi_dir)/env.yml
INPUT_CONFS        ?= example-tools.yml \
                      $(if $(filter $(tech_name),nangate45),\
                        example-nangate45.yml,\
                        $(if $(filter $(tech_name),asap7),\
							example-asap7.yml,\
							example-sky130.yml))
HAMMER_EXEC        ?= $(if $(filter $(tech_name),sky130),\
                        ./example-vlsi-sky130,\
                        ./example-vlsi)
VLSI_TOP           ?= $(TOP)
VLSI_HARNESS_DUT_NAME ?= chiptop
# If overriding, this should be relative to $(vlsi_dir)
VLSI_OBJ_DIR       ?= build
ifneq ($(CUSTOM_VLOG),)
	OBJ_DIR          ?= $(vlsi_dir)/$(VLSI_OBJ_DIR)/custom-$(VLSI_TOP)
else
	OBJ_DIR          ?= $(vlsi_dir)/$(VLSI_OBJ_DIR)/$(long_name)-$(VLSI_TOP)
endif

#########################################################################################
# general rules
#########################################################################################
ALL_RTL = $(TOP_FILE) $(TOP_SMEMS_FILE)
extra_v_includes = $(build_dir)/EICG_wrapper.v
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

SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/TestDriver.v

# copy files but ignore *.h files in *.f since vcs has +incdir+$(build_dir)
$(sim_files): $(SIM_FILE_REQS) | $(build_dir)
	cp -f $^ $(build_dir)
	$(foreach file,\
		$^,\
		$(if $(filter %.h,$(file)),\
			,\
			echo "$(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;))

SIM_CONF = $(OBJ_DIR)/sim-inputs.yml
SIM_DEBUG_CONF = $(OBJ_DIR)/sim-debug-inputs.yml
SIM_TIMING_CONF = $(OBJ_DIR)/sim-timing-inputs.yml

include $(vlsi_dir)/sim.mk
$(SIM_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files) $(dramsim_lib)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $(HARNESS_FILE) $(HARNESS_SMEMS_FILE); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  input_files_meta: 'append'" >> $@
	echo "  timescale: '1ns/10ps'" >> $@
	echo "  options:" >> $@
	for x in $(VCS_NONCC_OPTS); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  options_meta: 'append'" >> $@
	echo "  defines:" >> $@
	for x in $(subst +define+,,$(PREPROC_DEFINES)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  defines_meta: 'append'" >> $@
	echo "  compiler_cc_opts:" >> $@
	for x in $(filter-out "",$(VCS_CXXFLAGS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  compiler_cc_opts_meta: 'append'" >> $@
	echo "  compiler_ld_opts:" >> $@
	for x in $(filter-out "",$(VCS_LDFLAGS)); do \
		echo '    - "'$$x'"' >> $@; \
	done
	echo "  compiler_ld_opts_meta: 'append'" >> $@
	echo "  execution_flags_prepend: ['$(PERMISSIVE_ON)']" >> $@
	echo "  execution_flags_append: ['$(PERMISSIVE_OFF)']" >> $@
	echo "  execution_flags:" >> $@
	for x in $(SIM_FLAGS); do \
	  echo '    - "'$$x'"' >> $@; \
	done
	echo "  execution_flags_meta: 'append'" >> $@
ifneq ($(BINARY), )
	echo "  benchmarks: ['$(BINARY)']" >> $@
endif
	echo "  tb_dut: 'TestDriver.testHarness.$(VLSI_HARNESS_DUT_NAME)'" >> $@

$(SIM_DEBUG_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	mkdir -p $(output_dir)
	echo "sim.inputs:" > $@
	echo "  defines: ['DEBUG']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  execution_flags:" >> $@
	for x in $(VERBOSE_FLAGS) $(WAVEFORM_FLAG); do \
	  echo '    - "'$$x'"' >> $@; \
	done
	echo "  execution_flags_meta: 'append'" >> $@
	echo "  saif.mode: 'time'" >> $@
	echo "  saif.start_time: '0ns'" >> $@
	echo "  saif.end_time: '`bc <<< $(timeout_cycles)*$(CLOCK_PERIOD)`ns'" >> $@
ifdef USE_FSDB
	echo "  options:" >> $@
	echo '    - "-kdb"' >> $@
	echo "  options_meta: 'append'" >> $@
	echo "sim.outputs.waveforms: ['$(sim_out_name).fsdb']" >> $@
else
	echo "sim.outputs.waveforms: ['$(sim_out_name).vpd']" >> $@
endif

$(SIM_TIMING_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  defines: ['NTC']" >> $@
	echo "  defines_meta: 'append'" >> $@
	echo "  timing_annotated: 'true'" >> $@

POWER_CONF = $(OBJ_DIR)/power-inputs.yml
include $(vlsi_dir)/power.mk
$(POWER_CONF): $(VLSI_RTL) $(HARNESS_FILE) $(HARNESS_SMEMS_FILE) $(sim_common_files)
	mkdir -p $(dir $@)
	echo "power.inputs:" > $@
	echo "  tb_dut: 'testHarness/$(VLSI_HARNESS_DUT_NAME)'" >> $@
	echo "  database: '$(OBJ_DIR)/par-rundir/$(VLSI_TOP)_FINAL'" >> $@
ifneq ($(BINARY), )
	echo "  waveforms: [" >> $@
ifdef USE_FSDB
	echo "    '$(sim_out_name).fsdb'" >> $@
else
	echo "    '$(sim_out_name).vpd'" >> $@
endif
	echo "  ]" >> $@
endif
	echo "  start_times: ['0ns']" >> $@
	echo "  end_times: [" >> $@
	echo "    '`bc <<< $(timeout_cycles)*$(CLOCK_PERIOD)`ns'" >> $@
	echo "  ]" >> $@

#########################################################################################
# synthesis input configuration
#########################################################################################
SYN_CONF = $(OBJ_DIR)/inputs.yml
GENERATED_CONFS = $(SYN_CONF)
ifeq ($(CUSTOM_VLOG), )
	GENERATED_CONFS += $(SRAM_CONF)
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
	for x in $(VLSI_RTL) $(shell cat $(VLSI_BB)); do \
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
	rm -rf $(VLSI_OBJ_DIR) hammer-vlsi*.log __pycache__ output.json $(GENERATED_CONFS) $(gen_dir) $(SIM_CONF) $(SIM_DEBUG_CONF) $(SIM_TIMING_CONF) $(POWER_CONF)
