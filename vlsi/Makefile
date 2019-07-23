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
tech_name          ?=
tech_dir           ?= $(vlsi_dir)/hammer-$(tech_name)-plugin/$(tech_name)
SMEMS_COMP         ?= $(tech_dir)/sram-compiler.json
SMEMS_CACHE        ?= $(tech_dir)/sram-cache.json
SMEMS_HAMMER       ?= $(build_dir)/$(long_name).mems.hammer.json
MACROCOMPILER_MODE ?= -l $(SMEMS_COMP) --use-compiler -hir $(SMEMS_HAMMER)
OBJ_DIR            ?= $(vlsi_dir)/build
ENV_YML            ?= $(vlsi_dir)/bwrc-env.yml
INPUT_CONFS        ?= example.yml $(dir $(tech_dir))/bwrc.yml
HAMMER_EXEC        ?= ./example-vlsi

#########################################################################################
# general rules
#########################################################################################
ALL_RTL = $(TOP_FILE) $(TOP_SMEMS_FILE) $(extra_v_includes)
extra_v_includes = $(gen_dir)/EICG_wrapper.v

.PHONY: default verilog
default: all

all: drc lvs

verilog: $(ALL_RTL)

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# synthesis
#########################################################################################
SYN_CONF = $(OBJ_DIR)/inputs.yml
GENERATED_CONFS = $(SYN_CONF) $(SRAM_CONF)
ALL_CONFS = $(INPUT_CONFS) $(GENERATED_CONFS)
SYN_OUT_VLOG = $(OBJ_DIR)/syn-rundir/$(TOP).mapped.v
SYN_OUT_JSON = $(OBJ_DIR)/syn-rundir/syn-output.json
PAR_IN_JSON = $(OBJ_DIR)/par-input.json

$(SYN_CONF): $(ALL_RTL) $(extra_v_includes) $(sim_top_blackboxes)
	mkdir -p $(dir $@)
	echo "synthesis.inputs:" > $@
	echo "  top_module: $(TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $(ALL_RTL) $(extra_v_includes) `cat $(sim_top_blackboxes)`; do \
		echo '    - "'$$x'"' >> $@; \
	done

.PHONY: syn synthesis
syn: $(SYN_OUT_VLOG)
synthesis: syn

$(SYN_OUT_VLOG) $(SYN_OUT_JSON): $(ENV_YML) $(ALL_CONFS) $(ALL_RTL)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(ALL_CONFS), -p $(x)) --obj_dir $(OBJ_DIR) syn

$(PAR_IN_JSON): $(SYN_OUT_JSON)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $<, -p $(x)) -o $@ syn_to_par

#########################################################################################
# place and route (par/pnr/apr)
#########################################################################################
GDS = $(OBJ_DIR)/par-rundir/$(TOP).gds
PAR_OUT_JSON = $(OBJ_DIR)/par-rundir/par-output.json
DRC_IN_JSON = $(OBJ_DIR)/drc-input.json

.PHONY: par place-and-route
par: $(GDS)
place-and-route: par

$(GDS) $(PAR_OUT_JSON): $(PAR_IN_JSON) $(SYN_OUT_VLOG)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) -p $< --obj_dir $(OBJ_DIR) par

$(DRC_IN_JSON): $(PAR_IN_JSON) $(PAR_OUT_JSON)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$^, -p $(x)) -o $@ --obj_dir $(OBJ_DIR) par_to_drc

#########################################################################################
# drc
# TODO unimplemented
#########################################################################################
DRC_DB = $(OBJ_DIR)/drc-rundir/drc_results.db

.PHONY: drc
drc: $(DRC_DB)

$(DRC_DB): $(DRC_IN_JSON) $(GDS)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) -p $< --obj_dir $(OBJ_DIR) drc

#########################################################################################
# lvs
# TODO unimplemented
#########################################################################################
LVS_RPT = $(OBJ_DIR)/lvs-rundir/lvs_results.rpt
LVS_IN_JSON = $(OBJ_DIR)/lvs-input.json

.PHONY: lvs
lvs: $(LVS_RPT)

$(LVS_IN_JSON): $(PAR_IN_JSON) $(PAR_OUT_JSON)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$^, -p $(x)) -o $@ --obj_dir $(OBJ_DIR) par_to_lvs

$(LVS_RPT): $(LVS_IN_JSON) $(GDS)
	mkdir -p $(dir $@)
	$(HAMMER_EXEC) -e $(ENV_YML) -p $< --obj_dir $(OBJ_DIR) lvs

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(OBJ_DIR) hammer-vlsi*.log __pycache__ output.json $(GENERATED_CONFS) $(gen_dir)
