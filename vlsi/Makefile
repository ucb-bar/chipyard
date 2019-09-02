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
tech_dir           ?= $(if $(filter $(tech_name), asap7 saed32), $(vlsi_dir)/hammer/src/hammer-vlsi/technology/$(tech_name), $(vlsi_dir)/hammer-$(tech_name)-plugin/$(tech_name))
SMEMS_COMP         ?= $(tech_dir)/sram-compiler.json
cMEMS_CACHE        ?= $(tech_dir)/sram-cache.json
SMEMS_HAMMER       ?= $(build_dir)/$(long_name).mems.hammer.json
MACROCOMPILER_MODE ?= -l $(SMEMS_CACHE) -hir $(SMEMS_HAMMER)
OBJ_DIR            ?= $(vlsi_dir)/build
ENV_YML            ?= $(vlsi_dir)/bwrc-env.yml
INPUT_CONFS        ?= example.yml
HAMMER_EXEC        ?= ./example-vlsi

#########################################################################################
# general rules
#########################################################################################
ALL_RTL = $(TOP_FILE) $(TOP_SMEMS_FILE) $(extra_v_includes)
extra_v_includes = $(build_dir)/EICG_wrapper.v $(vlsi_dir)/example.v

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
SRAM_GENERATOR_CONF = $(build_dir)/sram_generator-input.yml
SRAM_CONF=$(build_dir)/sram_generator-output.json

## SRAM Generator
.PHONY: sram_generator srams
srams: sram_generator
sram_generator: $(SRAM_CONF)

# This should be built alongside $(SMEMS_FILE)
$(SMEMS_HAMMER): $(SMEMS_FILE)

$(SRAM_GENERATOR_CONF): $(SMEMS_HAMMER)
	mkdir -p $(dir $@)
	echo "vlsi.inputs.sram_parameters: '$(SMEMS_HAMMER)'" >> $@
	echo "vlsi.inputs.sram_parameters_meta: [\"transclude\", \"json2list\"]">> $@

$(SRAM_CONF): $(SRAM_GENERATOR_CONF)
	cd $(vlsi_dir) && $(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $(SRAM_GENERATOR_CONF), -p $(x)) --obj_dir $(build_dir) sram_generator
	cd $(vlsi_dir) && cp output.json $@

#########################################################################################
# synthesis input configuration
#########################################################################################
SYN_CONF = $(OBJ_DIR)/inputs.yml
GENERATED_CONFS = $(SYN_CONF) $(if $(filter $(tech_name), asap7), , $(SRAM_CONF))

$(SYN_CONF): $(ALL_RTL) $(extra_v_includes) $(sim_top_blackboxes)
	mkdir -p $(dir $@)
	echo "synthesis.inputs:" > $@
	echo "  top_module: $(TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $(ALL_RTL) $(extra_v_includes) `cat $(sim_top_blackboxes)`; do \
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
	rm -rf $(OBJ_DIR) hammer-vlsi*.log __pycache__ output.json $(GENERATED_CONFS) $(gen_dir)
