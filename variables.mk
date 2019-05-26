#########################################################################################
# makefile variables shared across multiple makefiles
#########################################################################################

#########################################################################################
# variables to invoke the generator
# descriptions:
#   SBT_PROJECT = the SBT project that you should find the classes/packages in
#   MODEL = the top level module of the project in Chisel (normally the harness)
#   VLOG_MODEL = the top level module of the project in Firrtl/Verilog (normally the harness)
#   MODEL_PACKAGE = the scala package to find the MODEL in
#   CONFIG = the configuration class to give the parameters for the project
#   CONFIG_PACKAGE = the scala package to find the CONFIG class
#   GENERATOR_PACKAGE = the scala package to find the Generator class in
#   TB = wrapper over the TestHarness needed to simulate in a verilog simulator
#   TOP = top level module of the project (normally the module instantiated by the harness)
#
# project specific:
# 	SUB_PROJECT = use the specific subproject default variables
#########################################################################################

#########################################################################################
# subproject overrides
# description:
#   - make it so that you only change 1 param to change most or all of them!
#   - mainly intended for quick developer setup for common flags
#########################################################################################
SUB_PROJECT ?= example

ifeq ($(SUB_PROJECT),example)
	SBT_PROJECT       ?= example
	MODEL             ?= BoomAndRocketTestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= $(SBT_PROJECT)
	CONFIG            ?= DefaultRocketConfig
	CONFIG_PACKAGE    ?= $(SBT_PROJECT)
	GENERATOR_PACKAGE ?= $(SBT_PROJECT)
	TB                ?= TestDriver
	TOP               ?= BoomAndRocketTop
endif
# for BOOM developers
ifeq ($(SUB_PROJECT),boom)
	SBT_PROJECT       ?= boom
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= boom.system
	CONFIG            ?= BoomConfig
	CONFIG_PACKAGE    ?= boom.system
	GENERATOR_PACKAGE ?= boom.system
	TB                ?= TestDriver
	TOP               ?= ExampleBoomAndRocketSystem
endif
# for Rocket-chip developers
ifeq ($(SUB_PROJECT),rocketchip)
	SBT_PROJECT       ?= rebarrocketchip
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= freechips.rocketchip.system
	CONFIG            ?= DefaultConfig
	CONFIG_PACKAGE    ?= freechips.rocketchip.system
	GENERATOR_PACKAGE ?= freechips.rocketchip.system
	TB                ?= TestDriver
	TOP               ?= ExampleRocketSystem
endif
# for Hwacha developers
ifeq ($(SUB_PROJECT),hwacha)
	SBT_PROJECT       ?= hwacha
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= freechips.rocketchip.system
	CONFIG            ?= HwachaConfig
	CONFIG_PACKAGE    ?= hwacha
	GENERATOR_PACKAGE ?= hwacha
	TB                ?= TestDriver
	TOP               ?= ExampleRocketSystem
endif

#########################################################################################
# path to rocket-chip and testchipip
#########################################################################################
ROCKETCHIP_DIR   = $(base_dir)/generators/rocket-chip
TESTCHIP_DIR     = $(base_dir)/generators/testchipip
REBAR_FIRRTL_DIR = $(base_dir)/tools/firrtl

#########################################################################################
# names of various files needed to compile and run things
#########################################################################################
long_name = $(MODEL_PACKAGE).$(MODEL).$(CONFIG)

# match the long_name to what the specific generator will output
ifeq ($(GENERATOR_PACKAGE),freechips.rocketchip.system)
	long_name=$(CONFIG_PACKAGE).$(CONFIG)
endif
ifeq ($(GENERATOR_PACKAGE),hwacha)
	long_name=$(MODEL_PACKAGE).$(CONFIG)
endif

FIRRTL_FILE        ?= $(build_dir)/$(long_name).fir
ANNO_FILE          ?= $(build_dir)/$(long_name).anno.json
VERILOG_FILE       ?= $(build_dir)/$(long_name).top.v
TOP_FIR            ?= $(build_dir)/$(long_name).top.fir
TOP_ANNO           ?= $(build_dir)/$(long_name).top.anno.json
HARNESS_FILE       ?= $(build_dir)/$(long_name).harness.v
HARNESS_FIR        ?= $(build_dir)/$(long_name).harness.fir
HARNESS_ANNO       ?= $(build_dir)/$(long_name).harness.anno.json
HARNESS_SMEMS_FILE ?= $(build_dir)/$(long_name).harness.mems.v
HARNESS_SMEMS_CONF ?= $(build_dir)/$(long_name).harness.mems.conf
HARNESS_SMEMS_FIR  ?= $(build_dir)/$(long_name).harness.mems.fir
SMEMS_FILE         ?= $(build_dir)/$(long_name).mems.v
SMEMS_CONF         ?= $(build_dir)/$(long_name).mems.conf
SMEMS_FIR          ?= $(build_dir)/$(long_name).mems.fir
sim_dotf               ?= $(build_dir)/sim_files.f
sim_harness_blackboxes ?= $(build_dir)/firrtl_black_box_resource_files.harness.f
sim_top_blackboxes     ?= $(build_dir)/firrtl_black_box_resource_files.top.f

#########################################################################################
# java arguments used in sbt
#########################################################################################
JAVA_ARGS ?= -Xmx8G -Xss8M -XX:MaxPermSize=256M

#########################################################################################
# default sbt launch command
#########################################################################################
SCALA_VERSION=2.12.4
SCALA_VERSION_MAJOR=$(basename $(SCALA_VERSION))

SBT ?= java $(JAVA_ARGS) -jar $(ROCKETCHIP_DIR)/sbt-launch.jar ++$(SCALA_VERSION)

#########################################################################################
# output directory for tests
#########################################################################################
output_dir=$(sim_dir)/output/$(long_name)

#########################################################################################
# helper variables to run binaries
#########################################################################################
BINARY ?=
SIM_FLAGS ?= +verbose
sim_out_name = $(notdir $(basename $(BINARY))).$(long_name)

#########################################################################################
# build output directory for compilation
#########################################################################################
gen_dir=$(sim_dir)/generated-src
build_dir=$(gen_dir)/$(long_name)

#########################################################################################
# vsrcs needed to run projects
#########################################################################################
rocketchip_vsrc_dir = $(ROCKETCHIP_DIR)/src/main/resources/vsrc

#########################################################################################
# sources needed to run simulators
#########################################################################################
sim_vsrcs = \
	$(VERILOG_FILE) \
	$(HARNESS_FILE) \
	$(SMEMS_FILE) \
	$(HARNESS_SMEMS_FILE)

#########################################################################################
# assembly/benchmark variables
#########################################################################################
timeout_cycles = 10000000
