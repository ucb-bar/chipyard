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
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# vlsi types and rules
#########################################################################################

#sim_name is unused, but GenerateSimFiles expects it
sim_name     ?= vcs
tech_name    ?= 
tech_dir     ?= $(vlsi_dir)/hammer-$(tech_name)-plugin/$(tech_name)
SMEMS_COMP   ?= $(tech_dir)/sram-compiler.json
SMEMS_HAMMER ?= $(build_dir)/$(long_name).mems.hammer.json
MACROCOMPILER_MODE ?= -l $(SMEMS_COMP) --use-compiler -hir $(SMEMS_HAMMER)
OBJ_DIR      ?= $(vlsi_dir)/build
ENV_YML      ?= $(vlsi_dir)/hammer-cad-plugins/bwrc-env.yml
INPUT_CONFS  ?= example.yml $(dir $(tech_dir))/bwrc.yml
HAMMER_EXEC  ?= ./example-vlsi

ROCKET_SRC_DIR=$(ROCKETCHIP_DIR)/src/main/resources/vsrc

ROCKET_SRCS = \
	$(ROCKET_SRC_DIR)/ClockDivider2.v \
	$(ROCKET_SRC_DIR)/ClockDivider3.v \
	$(ROCKET_SRC_DIR)/AsyncResetReg.v \
	$(ROCKET_SRC_DIR)/plusarg_reader.v \
	$(ROCKET_SRC_DIR)/EICG_wrapper.v \

ALL_RTL = $(ROCKET_SRCS) $(VERILOG_FILE) $(SMEMS_FILE)

CLOCK_DOMAINS = $(build_dir)/$(long_name).domains

.PHONY: default
default: all

all: drc lvs


###################################################### SYN ############################################################

SYNTH_CONF = $(OBJ_DIR)/inputs.yml

$(SYNTH_CONF):
	mkdir -p $(dir $@)
	echo "synthesis.inputs:" > $@
	echo "  top_module: $(TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $(ALL_RTL); do \
		echo '    - "'$$x'"' >> $@; \
	done

GENERATED_CONFS=$(SYNTH_CONF) $(SRAM_CONF)

.PHONY: syn synthesis
syn: $(OBJ_DIR)/syn-rundir/$(TOP).mapped.v
synthesis: syn

$(OBJ_DIR)/syn-rundir/$(TOP).mapped.v $(OBJ_DIR)/syn-rundir/syn-output.json: $(ENV_YML) $(INPUT_CONFS) $(GENERATED_CONFS) $(ALL_RTL)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $(GENERATED_CONFS), -p $(x)) --obj_dir $(OBJ_DIR) syn

$(OBJ_DIR)/par-input.json: $(OBJ_DIR)/syn-rundir/syn-output.json
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $<, -p $(x)) -o $@ syn_to_par

###################################################### PAR ############################################################

.PHONY: par place-and-route
par: $(OBJ_DIR)/par-rundir/$(TOP).gds
place-and-route: par

$(OBJ_DIR)/par-rundir/$(TOP).gds $(OBJ_DIR)/par-rundir/par-output.json: $(OBJ_DIR)/par-input.json $(OBJ_DIR)/syn-rundir/$(TOP).mapped.v
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) -p $< --obj_dir $(OBJ_DIR) par

$(OBJ_DIR)/drc-input.json: $(OBJ_DIR)/par-input.json $(OBJ_DIR)/par-rundir/par-output.json
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$^, -p $(x)) -o $@ --obj_dir $(OBJ_DIR) par_to_drc

###################################################### DRC ############################################################
# TODO unimplemented
.PHONY: drc
drc: $(OBJ_DIR)/drc-rundir/drc_results.db

$(OBJ_DIR)/drc-rundir/drc_results.db: $(OBJ_DIR)/drc-input.json $(OBJ_DIR)/par-rundir/$(TOP).gds
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) -p $< --obj_dir $(OBJ_DIR) drc

###################################################### LVS ############################################################
# TODO unimplemented
$(OBJ_DIR)/lvs-input.json: $(OBJ_DIR)/par-input.json $(OBJ_DIR)/par-rundir/par-output.json
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$^, -p $(x)) -o $@ --obj_dir $(OBJ_DIR) par_to_lvs

.PHONY: lvs
lvs: $(OBJ_DIR)/lvs-rundir/lvs_results.rpt

$(OBJ_DIR)/lvs-rundir/lvs_results.rpt: $(OBJ_DIR)/lvs-input.json $(OBJ_DIR)/par-rundir/$(TOP).gds
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) -p $< --obj_dir $(OBJ_DIR) lvs

.PHONY: clean
clean:
	rm -rf $(OBJ_DIR) hammer-vlsi*.log __pycache__ output.json $(GENERATED_CONFS) generated-src
