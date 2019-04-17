#########################################################################################
# makefile variables shared across multiple makefiles
#########################################################################################

#########################################################################################
# default variables to invoke the generator
# descriptions:
#   PROJECT = the scala package to find the MODEL/Generator in
#   MODEL = the top level module of the project (normally the harness)
#   CONFIG = the configuration class to give the parameters for the project
#   CFG_PROJECT = the scala package to find the CONFIG class
#   SBT_PROJECT = the SBT project that you should find the Generator class in
#   TB = wrapper over the TestHarness needed to simulate in VCS
#   TOP = top level module of the project (normally the module instantiated by the harness)
#########################################################################################
PROJECT     ?= example
MODEL       ?= TestHarness
CONFIG      ?= DefaultExampleConfig
CFG_PROJECT ?= $(PROJECT)
SBT_PROJECT ?= $(PROJECT)
TB          ?= TestDriver
TOP         ?= ExampleTop

#########################################################################################
# path to rocket-chip and testchipip
#########################################################################################
ROCKETCHIP_DIR = $(base_dir)/generators/rocket-chip
TESTCHIP_DIR   = $(base_dir)/generators/testchipip

#########################################################################################
# names of various files needed to compile and run things
#########################################################################################
long_name = $(PROJECT).$(MODEL).$(CONFIG)

FIRRTL_FILE  ?= $(build_dir)/$(long_name).fir
ANNO_FILE    ?= $(build_dir)/$(long_name).anno.json
VERILOG_FILE ?= $(build_dir)/$(long_name).top.v
TOP_FIR      ?= $(build_dir)/$(long_name).top.fir
TOP_ANNO     ?= $(build_dir)/$(long_name).top.anno.json
HARNESS_FILE ?= $(build_dir)/$(long_name).harness.v
HARNESS_FIR  ?= $(build_dir)/$(long_name).harness.fir
HARNESS_ANNO ?= $(build_dir)/$(long_name).harness.anno.json
SMEMS_FILE   ?= $(build_dir)/$(long_name).mems.v
SMEMS_CONF   ?= $(build_dir)/$(long_name).mems.conf
SMEMS_FIR    ?= $(build_dir)/$(long_name).mems.fir
sim_dotf     ?= $(build_dir)/sim_files.f
sim_harness_blackboxes ?= $(build_dir)/firrtl_black_box_resource_files.harness.f
sim_top_blackboxes ?= $(build_dir)/firrtl_black_box_resource_files.top.f

#########################################################################################
# default sbt launch command
#########################################################################################
SCALA_VERSION=2.12.4
SCALA_VERSION_MAJOR=$(basename $(SCALA_VERSION))

SBT ?= java -Xmx2G -Xss8M -XX:MaxPermSize=256M -jar $(ROCKETCHIP_DIR)/sbt-launch.jar ++$(SCALA_VERSION)

#########################################################################################
# output directory for tests
#########################################################################################
output_dir=$(sim_dir)/output

#########################################################################################
# build output directory for compilation
#########################################################################################
build_dir=$(sim_dir)/generated-src

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
	$(SMEMS_FILE)

#########################################################################################
# assembly/benchmark variables
#########################################################################################
timeout_cycles = 10000000
bmark_timeout_cycles = 100000000
