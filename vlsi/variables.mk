
#########################################################################################
# vlsi types and rules
#########################################################################################
sim_name           ?= vcs # needed for GenerateSimFiles, but is unused
tech_name          ?= asap7
tech_dir           ?= $(if $(filter $(tech_name),asap7 nangate45),\
                        $(vlsi_dir)/hammer/src/hammer-vlsi/technology/$(tech_name), \
                        $(vlsi_dir)/hammer-$(tech_name)-plugin/$(tech_name))
SMEMS_COMP         ?= $(tech_dir)/sram-compiler.json
SMEMS_CACHE        ?= $(tech_dir)/sram-cache.json
SMEMS_HAMMER       ?= $(build_dir)/$(long_name).top.mems.hammer.json
SMEMS_INSTMAP      ?= $(build_dir)/$(long_name).top.mems.instmap.txt

FLOORPLAN_FPIR     ?= $(build_dir)/$(long_name).top.fpir
FLOORPLAN_FILE     ?= $(build_dir)/$(long_name).top.floorplan.yml
FLOORPLAN_ASPECT   ?= chipyard.floorplan.RocketFloorplanAspect

ifdef USE_SRAM_COMPILER
MACROCOMPILER_MODE ?= -l $(SMEMS_COMP) --use-compiler -hir $(SMEMS_HAMMER) -im $(SMEMS_INSTMAP) --mode strict
else
MACROCOMPILER_MODE ?= -l $(SMEMS_CACHE) -hir $(SMEMS_HAMMER) -im $(SMEMS_INSTMAP) --mode strict
endif

ENV_YML            ?= $(vlsi_dir)/env.yml
INPUT_CONFS        ?= example-tools.yml \
                      $(if $(filter $(tech_name),nangate45),\
                        example-nangate45.yml,\
                        example-asap7.yml)
HAMMER_EXEC        ?= ./example-vlsi
VLSI_TOP           ?= $(TOP)
VLSI_HARNESS_DUT_NAME ?= chiptop
# If overriding, this should be relative to $(vlsi_dir)
VLSI_OBJ_DIR       ?= build

ifneq ($(CUSTOM_VLOG),)
OBJ_DIR            ?= $(vlsi_dir)/$(VLSI_OBJ_DIR)/custom-$(VLSI_TOP)
else
OBJ_DIR            ?= $(vlsi_dir)/$(VLSI_OBJ_DIR)/$(long_name)-$(VLSI_TOP)
endif

