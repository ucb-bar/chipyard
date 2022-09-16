#########################################################################################
# makefile variables for Hammer tutorials
#########################################################################################
tutorial ?= none

# TODO: eventually have asap7 commercial/openroad tutorial flavors
ifeq ($(tutorial),asap7)
	tech_name         ?= asap7
	CONFIG			  ?= TinyRocketConfig
endif

ifeq ($(tutorial),sky130-commercial)
	tech_name         ?= sky130
	CONFIG			  ?= TinyRocketConfig
	TOOLS_CONF	      ?= example-tools.yml
	TECH_CONF		  ?= example-sky130.yml
	DESIGN_CONF		  ?= example-design-sky130-commercial.yml
	INPUT_CONFS		  ?= $(TOOLS_CONF) $(TECH_CONF) $(DESIGN_CONF)
endif

ifeq ($(tutorial),sky130-openroad)
	tech_name         ?= sky130
	CONFIG			  ?= TinyRocketConfig
	TOOLS_CONF	      ?= example-openroad.yml
	TECH_CONF		  ?= example-sky130.yml
	DESIGN_CONF		  ?= example-design-sky130-openroad.yml
	INPUT_CONFS		  ?= $(TOOLS_CONF) $(TECH_CONF) $(DESIGN_CONF)
endif
