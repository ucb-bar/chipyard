# Ensure GNU Make uses a modern bash for recipes
# - Set early (before other includes) so old make versions honor it.
# - Prefer Homebrew or /usr/local bash, else fall back to /bin/bash.
SHELL := $(shell \
  if [ -x /opt/homebrew/bin/bash ]; then printf /opt/homebrew/bin/bash; \
  elif [ -x /usr/local/bin/bash ]; then printf /usr/local/bin/bash; \
  elif command -v bash >/dev/null 2>&1; then command -v bash; \
  else printf /bin/bash; fi)

# Portable sed in-place flag (GNU vs BSD/macOS)
SED ?= sed
SED_IS_GNU := $(shell sed --version >/dev/null 2>&1 && echo yes || echo no)
ifeq ($(SED_IS_GNU),yes)
SED_INPLACE := -i
else
SED_INPLACE := -i ''
endif

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
"   FIRTOOL_BIN       = path to CIRCT firtool (default: 'firtool' in PATH)" \
"   USE_CHISEL7       = EXPERIMENTAL: set to '1' to build with Chisel 7" \

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
"   BINARIES_DIR           = directory of riscv elf binaries that the simulator will run when using the run-binaries* targets" \
"   BINARY_ARGS            = arguments to pass to each binary in run-binary targets (primarily meant for pk arguments)" \
"   LOADMEM                = riscv elf binary that should be loaded directly into simulated DRAM. LOADMEM=1 will load the BINARY elf" \
"   LOADARCH               = path to a architectural checkpoint directory that should end in .loadarch/, for restoring from a checkpoint" \
"   VERBOSE_FLAGS          = flags used when doing verbose simulation [$(VERBOSE_FLAGS)]" \
"   TIMEOUT_CYCLES         = number of clock cycles before simulator times out, defaults to 10000000" \
"   DUMP_BINARY            = set to '1' to disassemble the target binary"

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
# For TestChipIP developers running unit-tests
ifeq ($(SUB_PROJECT),testchipip)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= TestChipUnitTestConfig
	CONFIG_PACKAGE    ?= testchipip.test
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
# For Radiance developers
ifeq ($(SUB_PROJECT),muon)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= MuonTestConfig
	CONFIG_PACKAGE    ?= radiance.unittest
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif

ifeq ($(SUB_PROJECT),coalescer)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= CoalescingUnitTestConfig
	CONFIG_PACKAGE    ?= radiance.unittest
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= TestDriver
	TOP               ?= UnitTestSuite
endif
ifeq ($(SUB_PROJECT),tensor)
	SBT_PROJECT       ?= chipyard
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.unittest
	CONFIG            ?= TensorUnitTestConfig
	CONFIG_PACKAGE    ?= radiance.unittest
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

# classpaths
CLASSPATH_CACHE ?= $(base_dir)/.classpath_cache
# The generator classpath must contain the Generator main
GENERATOR_CLASSPATH ?= $(CLASSPATH_CACHE)/$(SBT_PROJECT).jar
# The tapeout classpath must contain MacroCompiler
TAPEOUT_CLASSPATH ?= $(CLASSPATH_CACHE)/tapeout.jar

# chisel generated outputs
FIRRTL_FILE ?= $(build_dir)/$(long_name).fir
ANNO_FILE   ?= $(build_dir)/$(long_name).anno.json
CHISEL_LOG_FILE ?= $(build_dir)/$(long_name).chisel.log
FIRTOOL_LOG_FILE ?= $(build_dir)/$(long_name).firtool.log

# Allow users to override the CIRCT FIRRTL compiler binary
FIRTOOL_BIN ?= firtool

# chisel anno modification output
MFC_EXTRA_ANNO_FILE ?= $(build_dir)/$(long_name).extrafirtool.anno.json
FINAL_ANNO_FILE ?= $(build_dir)/$(long_name).appended.anno.json

# firtool compiler outputs
MFC_TOP_HRCHY_JSON ?= $(build_dir)/top_module_hierarchy.json
MFC_MODEL_HRCHY_JSON ?= $(build_dir)/model_module_hierarchy.json
MFC_MODEL_HRCHY_JSON_UNIQUIFIED ?= $(build_dir)/model_module_hierarchy.uniquified.json
MFC_SMEMS_CONF ?= $(build_dir)/$(long_name).mems.conf
# hardcoded firtool outputs
MFC_FILELIST = $(GEN_COLLATERAL_DIR)/filelist.f
MFC_BB_MODS_FILELIST = $(GEN_COLLATERAL_DIR)/firrtl_black_box_resource_files.f
MFC_TOP_SMEMS_JSON = $(GEN_COLLATERAL_DIR)/metadata/seq_mems.json

# macrocompiler smems in/output
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

# external filelists. Users, or project-supplied make fragments can append filelists
# with absolute paths here
EXT_FILELISTS ?=
# external verilog incdirs. Users, or project-supplied make fragments can append to this
EXT_INCDIRS ?=

# files that contain lists of files needed for VCS or Verilator simulation
SIM_FILE_REQS =
sim_files              ?= $(build_dir)/sim_files.f
# single file that contains all files needed for VCS or Verilator simulation (unique and without .h's)
sim_common_files       ?= $(build_dir)/sim_files.common.f

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
SBT ?= java -jar $(base_dir)/scripts/sbt-launch.jar $(SBT_OPTS)

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
BINARY_ARGS ?=
override SIM_FLAGS += +dramsim +dramsim_ini_dir=$(TESTCHIP_DIR)/src/main/resources/dramsim2_ini +max-cycles=$(TIMEOUT_CYCLES)
VERBOSE_FLAGS ?= +verbose
# get_out_name is a function, 1st argument is the binary
get_out_name = $(subst $() $(),_,$(notdir $(basename $(1))))
LOADMEM ?=
LOADARCH ?=
DUMP_BINARY ?= 1

ifneq ($(LOADARCH),)
override BINARY = $(addsuffix /mem.elf,$(LOADARCH))
override BINARIES = $(addsuffix /mem.elf,$(LOADARCH))
override get_out_name = $(shell basename $(dir $(1)))
override LOADMEM = 1
endif

ifneq ($(BINARIES_DIR),)
override BINARIES = $(shell find -L $(BINARIES_DIR) -type f -print 2> /dev/null)
endif

#########################################################################################
# build output directory for compilation
#########################################################################################
# output for all project builds
generated_src_name ?=generated-src
gen_dir             =$(sim_dir)/$(generated_src_name)
# per-project output directory
build_dir           =$(gen_dir)/$(long_name)
# final generated collateral per-project
GEN_COLLATERAL_DIR ?=$(build_dir)/gen-collateral

#########################################################################################
# simulation variables
#########################################################################################
TIMEOUT_CYCLES = 10000000

# legacy timeout_cycles handling
timeout_cycles ?=
ifneq ($(timeout_cycles),)
TIMEOUT_CYCLES=$(timeout_cycles)
endif
