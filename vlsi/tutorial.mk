#########################################################################################
# makefile variables for Hammer tutorials
#########################################################################################
tutorial ?= none

# TODO: eventually have asap7 commercial/openroad tutorial flavors
ifeq ($(tutorial),asap7)
    tech_name         ?= asap7
    CONFIG            ?= TinyRocketConfig
    TOOLS_CONF        ?= example-tools.yml
    TECH_CONF         ?= example-asap7.yml
    INPUT_CONFS       ?= $(EXTRA_CONFS) $(TOOLS_CONF) $(TECH_CONF)
    VLSI_OBJ_DIR      ?= build-asap7-commercial
endif

ifeq ($(tutorial),sky130-commercial)
    tech_name         ?= sky130
    CONFIG            ?= TinyRocketConfig
    TOOLS_CONF        ?= example-tools.yml
    TECH_CONF         ?= example-sky130.yml
    DESIGN_CONF       ?= example-designs/sky130-commercial.yml
    EXTRA_CONFS       ?= $(if $(filter $(VLSI_TOP),Rocket), example-designs/sky130-rocket.yml, )
    INPUT_CONFS       ?= $(TOOLS_CONF) $(TECH_CONF) $(DESIGN_CONF) $(EXTRA_CONFS)
    VLSI_OBJ_DIR      ?= build-sky130-commercial
endif

ifeq ($(tutorial),sky130-openroad)
    tech_name         ?= sky130
    CONFIG            ?= TinyRocketConfig
    TOOLS_CONF        ?= example-openroad.yml
    TECH_CONF         ?= example-sky130.yml
    DESIGN_CONF       ?= example-designs/sky130-openroad.yml
    EXTRA_CONFS       ?= $(if $(filter $(VLSI_TOP),Rocket), example-designs/sky130-rocket.yml, )
    INPUT_CONFS       ?= $(TOOLS_CONF) $(TECH_CONF) $(DESIGN_CONF) $(EXTRA_CONFS)
    VLSI_OBJ_DIR      ?= build-sky130-openroad
    # Yosys compatibility for CIRCT-generated Verilog, at the expense of elaboration time.
    ENABLE_CUSTOM_FIRRTL_PASS = 1
endif
