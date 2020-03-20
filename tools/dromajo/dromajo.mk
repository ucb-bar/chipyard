DROMAJO_DIR = $(base_dir)/tools/dromajo/dromajo-src/src
DROMAJO_LIB = $(DROMAJO_DIR)/libdromajo_cosim.a

# Dromajo assumes using the default bootrom
DROMAJO_ROM = $(base_dir)/bootrom/bootrom.rv64.img

DTS_FILE = $(build_dir)/$(long_name).dts
DTB_FILE = $(build_dir)/$(long_name).dtb

$(DTS_FILE) $(DROMAJO_PARAMS_FILE): $(FIRRTL_FILE)

$(DTB_FILE): $(DTS_FILE)
	dtc -I dts -O dtb -o $(DTB_FILE) $(DTS_FILE)

DROMAJO_SRCS = $(call lookup_srcs,$(DROMAJO_DIR),cc) $(call lookup_srcs,$(DROMAJO_DIR),h)

$(DROMAJO_LIB): $(DROMAJO_SRCS)
	$(MAKE) -C $(DROMAJO_DIR)

ifeq ($(BINARY),)
DROMAJO_BIN = $(<)
else
DROMAJO_BIN = $(BINARY)
endif

DROMAJO_FLAGS = +drj_dtb=$(DTB_FILE) +drj_rom=$(DROMAJO_ROM) +drj_bin=$(DROMAJO_BIN)

DROMAJO_PARAMS_FILE    = $(build_dir)/$(long_name).dromajo_params.h
DROMAJO_PARAMS_SYMLINK = $(build_dir)/dromajo_params.h

$(DROMAJO_PARAMS_SYMLINK): $(DROMAJO_PARAMS_FILE)
	ln -s $(DROMAJO_PARAMS_FILE) $(DROMAJO_PARAMS_SYMLINK)

##################################################################
# THE FOLLOWING MUST BE += operators
##################################################################

# sourced used to run the generator
PROJECT_GENERATOR_SOURCES +=

# simargs needed (i.e. like +drj_test=hello)
PROJECT_SIM_FLAGS += $(DROMAJO_FLAGS)

# extra vcs compile flags
PROJECT_VCS_FLAGS += -CC "-I$(DROMAJO_DIR)" $(DROMAJO_LIB)

# extra verilator compile flags
PROJECT_VERILATOR_FLAGS += -CFLAGS "-I$(DROMAJO_DIR)" -LDFLAGS "-L$(DROMAJO_DIR) -Wl,-rpath,$(DROMAJO_DIR)"

# extra simulation sources needed for VCS/Verilator compile
PROJECT_SIM_SOURCES += $(DROMAJO_PARAMS_SYMLINK) $(DROMAJO_LIB)
