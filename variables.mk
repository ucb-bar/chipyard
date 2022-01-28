#########################################################################################
# makefile variables shared across multiple makefiles
# - to use the help text, your Makefile should have a 'help' target that just
#   prints all the HELP_LINES
#########################################################################################
HELP_COMPILATION_VARIABLES = \
"   JAVA_HEAP_SIZE    = if overridden, set the default java heap size (default is 8G)" \
"   JAVA_TOOL_OPTIONS = if overridden, set underlying java tool options (default sets misc. sizes and tmp dir)" \
"   SBT_OPTS          = set additional sbt command line options (these take the form -Dsbt.<option>=<setting>) " \
"                       See https://www.scala-sbt.org/1.x/docs/Command-Line-Reference.html\#Command+Line+Options" \
"   SBT_BIN           = if overridden, used to invoke sbt (default is to invoke sbt by sbt-launch.jar)" \
"   FIRRTL_LOGLEVEL   = if overridden, set firrtl log level (default is error)"

HELP_PROJECT_VARIABLES = \
"   SUB_PROJECT            = use the specific subproject default variables [$(SUB_PROJECT)]" \
"   SBT_PROJECT            = the SBT project that you should find the classes/packages in [$(SBT_PROJECT)]" \
"   MODEL                  = the top level module of the project in Chisel (normally the harness) [$(MODEL)]" \
"   VLOG_MODEL             = the top level module of the project in Firrtl/Verilog (normally the harness) [$(VLOG_MODEL)]" \
"   MODEL_PACKAGE          = the scala package to find the MODEL in [$(MODEL_PACKAGE)]" \
"   CONFIG                 = the configuration class to give the parameters for the project [$(CONFIG)]" \
"   CONFIG_PACKAGE         = the scala package to find the CONFIG class [$(CONFIG_PACKAGE)]" \
"   GENERATOR_PACKAGE      = the scala package to find the Generator class in [$(GENERATOR_PACKAGE)]" \
"   TB                     = testbench wrapper over the TestHarness needed to simulate in a verilog simulator [$(TB)]" \
"   TOP                    = top level module of the project (normally the module instantiated by the harness) [$(TOP)]"

HELP_SIMULATION_VARIABLES = \
"   BINARY                 = riscv elf binary that the simulator will run when using the run-binary* targets" \
"   VERBOSE_FLAGS          = flags used when doing verbose simulation [$(VERBOSE_FLAGS)]" \
"   timeout_cycles         = number of clock cycles before simulator times out, defaults to 10000000" \
"   bmark_timeout_cycles   = number of clock cycles before benchmark simulator times out, defaults to 100000000"

# include default simulation rules
HELP_COMMANDS = \
"   help                   = display this help" \
"   default                = compiles non-debug simulator [./$(shell basename $(sim))]" \
"   debug                  = compiles debug simulator [./$(shell basename $(sim_debug))]" \
"   clean                  = remove all debug/non-debug simulators and intermediate files" \
"   clean-sim              = removes non-debug simulator and simulator-generated files" \
"   clean-sim-debug        = removes debug simulator and simulator-generated files"

HELP_LINES = "" \
	" design specifier variables:" \
	" ---------------------------" \
	$(HELP_PROJECT_VARIABLES) \
	"" \
	" compilation variables:" \
	" ----------------------" \
	$(HELP_COMPILATION_VARIABLES) \
	"" \
	" simulation variables:" \
	" ---------------------" \
	$(HELP_SIMULATION_VARIABLES) \
	"" \
	" some useful general commands:" \
	" -----------------------------" \
	$(HELP_COMMANDS) \
	""

#########################################################################################
# subproject overrides
# description:
#   - make it so that you only change 1 param to change most or all of them!
#   - mainly intended for quick developer setup for common flags
#########################################################################################
SUB_PROJECT ?= chipyard

ifeq ($(SUB_PROJECT),chipyard)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= $(SBT_PROJECT)
	CONFIG            ?= RocketConfig
	CONFIG_PACKAGE    ?= $(SBT_PROJECT)
	GENERATOR_PACKAGE ?= $(SBT_PROJECT)
	TB                ?= TestDriver
	TOP               ?= ChipTop
endif
# for Hwacha developers
ifeq ($(SUB_PROJECT),hwacha)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= freechips.rocketchip.system
	CONFIG            ?= HwachaConfig
	CONFIG_PACKAGE    ?= hwacha
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= ExampleRocketSystem
endif
# For TestChipIP developers
ifeq ($(SUB_PROJECT),testchipip)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= TestChipUnitTestConfig
	CONFIG_PACKAGE    ?= testchipip
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif
# For IceNet developers
ifeq ($(SUB_PROJECT),icenet)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= TestHarness
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= IceNetUnitTestConfig
	CONFIG_PACKAGE    ?= icenet
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif

#########################################################################################
# path to rocket-chip and testchipip
#########################################################################################
ROCKETCHIP_DIR       = $(base_dir)/generators/rocket-chip
ROCKETCHIP_RSRCS_DIR = $(ROCKETCHIP_DIR)/src/main/resources
TESTCHIP_DIR         = $(base_dir)/generators/testchipip
TESTCHIP_RSRCS_DIR   = $(TESTCHIP_DIR)/src/main/resources
CHIPYARD_FIRRTL_DIR  = $(base_dir)/tools/firrtl
CHIPYARD_RSRCS_DIR   = $(base_dir)/generators/chipyard/src/main/resources

#########################################################################################
# names of various files needed to compile and run things
#########################################################################################
long_name = $(MODEL_PACKAGE).$(MODEL).$(CONFIG)
ifeq ($(GENERATOR_PACKAGE),hwacha)
	long_name=$(MODEL_PACKAGE).$(CONFIG)
endif

FIRRTL_FILE ?= $(build_dir)/$(long_name).fir
ANNO_FILE   ?= $(build_dir)/$(long_name).anno.json

TOP_FILE       ?= $(build_dir)/$(long_name).top.v
TOP_FIR        ?= $(build_dir)/$(long_name).top.fir
TOP_ANNO       ?= $(build_dir)/$(long_name).top.anno.json
TOP_SMEMS_FILE ?= $(build_dir)/$(long_name).top.mems.v
TOP_SMEMS_CONF ?= $(build_dir)/$(long_name).top.mems.conf
TOP_SMEMS_FIR  ?= $(build_dir)/$(long_name).top.mems.fir

HARNESS_FILE       ?= $(build_dir)/$(long_name).harness.v
HARNESS_FIR        ?= $(build_dir)/$(long_name).harness.fir
HARNESS_ANNO       ?= $(build_dir)/$(long_name).harness.anno.json
HARNESS_SMEMS_FILE ?= $(build_dir)/$(long_name).harness.mems.v
HARNESS_SMEMS_CONF ?= $(build_dir)/$(long_name).harness.mems.conf
HARNESS_SMEMS_FIR  ?= $(build_dir)/$(long_name).harness.mems.fir

BOOTROM_FILES   ?= bootrom.rv64.img bootrom.rv32.img
BOOTROM_TARGETS ?= $(addprefix $(build_dir)/, $(BOOTROM_FILES))

# files that contain lists of files needed for VCS or Verilator simulation
SIM_FILE_REQS =
sim_files              ?= $(build_dir)/sim_files.f
sim_top_blackboxes     ?= $(build_dir)/firrtl_black_box_resource_files.top.f
sim_harness_blackboxes ?= $(build_dir)/firrtl_black_box_resource_files.harness.f
# single file that contains all files needed for VCS or Verilator simulation (unique and without .h's)
sim_common_files       ?= $(build_dir)/sim_files.common.f

#########################################################################################
# java arguments used in sbt
#########################################################################################
JAVA_HEAP_SIZE ?= 8G
export JAVA_TOOL_OPTIONS ?= -Xmx$(JAVA_HEAP_SIZE) -Xss8M -Djava.io.tmpdir=$(base_dir)/.java_tmp

#########################################################################################
# default sbt launch command
#########################################################################################
SCALA_BUILDTOOL_DEPS = $(SBT_SOURCES)

SBT_THIN_CLIENT_TIMESTAMP = $(base_dir)/project/target/active.json

ifdef ENABLE_SBT_THIN_CLIENT
SCALA_BUILDTOOL_DEPS += $(SBT_THIN_CLIENT_TIMESTAMP)
# enabling speeds up sbt loading
# use with sbt script or sbtn to bypass error code issues
SBT_CLIENT_FLAG = --client
endif

# passes $(JAVA_TOOL_OPTIONS) from env to java
SBT_BIN ?= java -jar $(ROCKETCHIP_DIR)/sbt-launch.jar $(SBT_OPTS)
SBT = $(SBT_BIN) $(SBT_CLIENT_FLAG)
SBT_NON_THIN = $(subst $(SBT_CLIENT_FLAG),,$(SBT))

define run_scala_main
	cd $(base_dir) && $(SBT) ";project $(1); runMain $(2) $(3)"
endef

FIRRTL_LOGLEVEL ?= error

#########################################################################################
# output directory for tests
#########################################################################################
output_dir=$(sim_dir)/output/$(long_name)

#########################################################################################
# helper variables to run binaries
#########################################################################################
PERMISSIVE_ON=+permissive
PERMISSIVE_OFF=+permissive-off
BINARY ?=
LOADMEM ?=
LOADMEM_ADDR ?= 81000000
override SIM_FLAGS += +dramsim +dramsim_ini_dir=$(TESTCHIP_DIR)/src/main/resources/dramsim2_ini +max-cycles=$(timeout_cycles)
ifneq ($(LOADMEM),)
override SIM_FLAGS += +loadmem=$(LOADMEM) +loadmem_addr=$(LOADMEM_ADDR)
endif
VERBOSE_FLAGS ?= +verbose
sim_out_name = $(output_dir)/$(subst $() $(),_,$(notdir $(basename $(BINARY))))
binary_hex= $(sim_out_name).loadmem_hex

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
	$(TOP_FILE) \
	$(HARNESS_FILE) \
	$(TOP_SMEMS_FILE) \
	$(HARNESS_SMEMS_FILE)

#########################################################################################
# assembly/benchmark variables
#########################################################################################
timeout_cycles = 10000000
bmark_timeout_cycles = 100000000
