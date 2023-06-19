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
"   SBT               = if overridden, used to invoke sbt (default is to invoke sbt by sbt-launch.jar)" \
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
"   BINARIES               = list of riscv elf binary that the simulator will run when using the run-binaries* targets" \
"   LOADMEM                = riscv elf binary that should be loaded directly into simulated DRAM. LOADMEM=1 will load the BINARY elf" \
"   LOADARCH               = path to a architectural checkpoint directory that should end in .loadarch/, for restoring from a checkpoint" \
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
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.harness
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
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= freechips.rocketchip.system
	CONFIG            ?= HwachaConfig
	CONFIG_PACKAGE    ?= hwacha
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= ExampleRocketSystem
endif
# For TestChipIP developers running unit-tests
ifeq ($(SUB_PROJECT),testchipip)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= TestChipUnitTestConfig
	CONFIG_PACKAGE    ?= testchipip
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif
# For rocketchip developers running unit-tests
ifeq ($(SUB_PROJECT),rocketchip)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= TLSimpleUnitTestConfig
	CONFIG_PACKAGE    ?= freechips.rocketchip.unittest
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif
# For IceNet developers
ifeq ($(SUB_PROJECT),icenet)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= IceNetUnitTestConfig
	CONFIG_PACKAGE    ?= icenet
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif
# For Constellation developers
ifeq ($(SUB_PROJECT),constellation)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= constellation.test
	CONFIG            ?= TestConfig00
	CONFIG_PACKAGE    ?= constellation.test
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= NoC
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

# classpaths
CLASSPATH_CACHE ?= $(base_dir)/.classpath_cache
CHIPYARD_CLASSPATH ?= $(CLASSPATH_CACHE)/chipyard.jar
TAPEOUT_CLASSPATH ?= $(CLASSPATH_CACHE)/tapeout.jar
# if *_CLASSPATH is a true java classpath, it can be colon-delimited list of paths (on *nix)
CHIPYARD_CLASSPATH_TARGETS ?= $(subst :, ,$(CHIPYARD_CLASSPATH))
TAPEOUT_CLASSPATH_TARGETS ?= $(subst :, ,$(TAPEOUT_CLASSPATH))

# chisel generated outputs
FIRRTL_FILE ?= $(build_dir)/$(long_name).fir
ANNO_FILE   ?= $(build_dir)/$(long_name).anno.json
EXTRA_ANNO_FILE ?= $(build_dir)/$(long_name).extra.anno.json
CHISEL_LOG_FILE ?= $(build_dir)/$(long_name).chisel.log

# chisel anno modification output
MFC_EXTRA_ANNO_FILE ?= $(build_dir)/$(long_name).extrafirtool.anno.json
FINAL_ANNO_FILE ?= $(build_dir)/$(long_name).appended.anno.json

# scala firrtl compiler (sfc) outputs
SFC_FIRRTL_BASENAME ?= $(build_dir)/$(long_name).sfc
SFC_FIRRTL_FILE ?= $(SFC_FIRRTL_BASENAME).fir
SFC_EXTRA_ANNO_FILE ?= $(build_dir)/$(long_name).extrasfc.anno.json
SFC_ANNO_FILE ?= $(build_dir)/$(long_name).sfc.anno.json

# firtool compiler outputs
MFC_TOP_HRCHY_JSON ?= $(build_dir)/top_module_hierarchy.json
MFC_MODEL_HRCHY_JSON ?= $(build_dir)/model_module_hierarchy.json
MFC_MODEL_HRCHY_JSON_UNIQUIFIED ?= $(build_dir)/model_module_hierarchy.uniquified.json
MFC_SMEMS_CONF ?= $(build_dir)/$(long_name).mems.conf
# hardcoded firtool outputs
MFC_FILELIST = $(GEN_COLLATERAL_DIR)/filelist.f
MFC_BB_MODS_FILELIST = $(GEN_COLLATERAL_DIR)/firrtl_black_box_resource_files.f
MFC_TOP_SMEMS_JSON = $(GEN_COLLATERAL_DIR)/metadata/seq_mems.json
MFC_MODEL_SMEMS_JSON = $(GEN_COLLATERAL_DIR)/metadata/tb_seq_mems.json

# macrocompiler smems in/output
SFC_SMEMS_CONF ?= $(build_dir)/$(long_name).sfc.mems.conf
TOP_SMEMS_CONF ?= $(build_dir)/$(long_name).top.mems.conf
TOP_SMEMS_FILE ?= $(GEN_COLLATERAL_DIR)/$(long_name).top.mems.v
TOP_SMEMS_FIR  ?= $(build_dir)/$(long_name).top.mems.fir
MODEL_SMEMS_CONF ?= $(build_dir)/$(long_name).model.mems.conf
MODEL_SMEMS_FILE ?= $(GEN_COLLATERAL_DIR)/$(long_name).model.mems.v
MODEL_SMEMS_FIR  ?= $(build_dir)/$(long_name).model.mems.fir

# top module files to include
TOP_MODS_FILELIST ?= $(build_dir)/$(long_name).top.f
# model module files to include (not including top modules)
MODEL_MODS_FILELIST ?= $(build_dir)/$(long_name).model.f
# list of all blackbox files (may be included in the top/model.f files)
# this has the build_dir appended
BB_MODS_FILELIST ?= $(build_dir)/$(long_name).bb.f
# all module files to include (top, model, bb included)
ALL_MODS_FILELIST ?= $(build_dir)/$(long_name).all.f

BOOTROM_FILES   ?= bootrom.rv64.img bootrom.rv32.img
BOOTROM_TARGETS ?= $(addprefix $(build_dir)/, $(BOOTROM_FILES))

# files that contain lists of files needed for VCS or Verilator simulation
SIM_FILE_REQS =
sim_files              ?= $(build_dir)/sim_files.f
# single file that contains all files needed for VCS or Verilator simulation (unique and without .h's)
sim_common_files       ?= $(build_dir)/sim_files.common.f

SFC_LEVEL ?= $(build_dir)/.sfc_level
EXTRA_FIRRTL_OPTIONS ?= $(build_dir)/.extra_firrtl_options
MFC_LOWERING_OPTIONS ?= $(build_dir)/.mfc_lowering_options

#########################################################################################
# java arguments used in sbt
#########################################################################################
JAVA_HEAP_SIZE ?= 8G
JAVA_TMP_DIR ?= $(base_dir)/.java_tmp
export JAVA_TOOL_OPTIONS ?= -Xmx$(JAVA_HEAP_SIZE) -Xss8M -Djava.io.tmpdir=$(JAVA_TMP_DIR)

#########################################################################################
# default sbt launch command
#########################################################################################
SCALA_BUILDTOOL_DEPS = $(SBT_SOURCES)

# passes $(JAVA_TOOL_OPTIONS) from env to java
export SBT_OPTS ?= -Dsbt.ivy.home=$(base_dir)/.ivy2 -Dsbt.global.base=$(base_dir)/.sbt -Dsbt.boot.directory=$(base_dir)/.sbt/boot/ -Dsbt.color=always -Dsbt.supershell=false -Dsbt.server.forcestart=true
SBT ?= java -jar $(ROCKETCHIP_DIR)/sbt-launch.jar $(SBT_OPTS)

# (1) - classpath of the fat jar
# (2) - main class
# (3) - main class arguments
define run_jar_scala_main
	cd $(base_dir) && java -cp $(1) $(2) $(3)
endef

# (1) - sbt project
# (2) - main class
# (3) - main class arguments
define run_scala_main
	cd $(base_dir) && $(SBT) ";project $(1); runMain $(2) $(3)"
endef

# (1) - sbt project to assemble
# (2) - classpath file(s) to create
define run_sbt_assembly
	cd $(base_dir) && $(SBT) ";project $(1); set assembly / assemblyOutputPath := file(\"$(2)\"); assembly" && touch $(2)
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
BINARIES ?=
override SIM_FLAGS += +dramsim +dramsim_ini_dir=$(TESTCHIP_DIR)/src/main/resources/dramsim2_ini +max-cycles=$(timeout_cycles)
VERBOSE_FLAGS ?= +verbose
# get_out_name is a function, 1st argument is the binary
get_out_name = $(subst $() $(),_,$(notdir $(basename $(1))))
LOADMEM ?=
LOADARCH ?=

ifneq ($(LOADARCH),)
override BINARY = $(addsuffix /mem.elf,$(LOADARCH))
override BINARIES = $(addsuffix /mem.elf,$(LOADARCH))
override get_out_name = $(shell basename $(dir $(1)))
override LOADMEM = 1
endif

#########################################################################################
# build output directory for compilation
#########################################################################################
# output for all project builds
gen_dir=$(sim_dir)/generated-src
# per-project output directory
build_dir=$(gen_dir)/$(long_name)
# final generated collateral per-project
GEN_COLLATERAL_DIR ?= $(build_dir)/gen-collateral

#########################################################################################
# assembly/benchmark variables
#########################################################################################
timeout_cycles = 10000000
bmark_timeout_cycles = 100000000

#########################################################################################
# locale settings
# some of the sbt/java commands will error on specific locales
#########################################################################################
export LANG=en_US.UTF-8
export LC_CTYPE="en_US.UTF-8"
export LC_NUMERIC="en_US.UTF-8"
export LC_TIME="en_US.UTF-8"
export LC_COLLATE="en_US.UTF-8"
export LC_MONETARY="en_US.UTF-8"
export LC_MESSAGES="en_US.UTF-8"
export LC_PAPER="en_US.UTF-8"
export LC_NAME="en_US.UTF-8"
export LC_ADDRESS="en_US.UTF-8"
export LC_TELEPHONE="en_US.UTF-8"
export LC_MEASUREMENT="en_US.UTF-8"
export LC_IDENTIFICATION="en_US.UTF-8"
export LC_ALL=
