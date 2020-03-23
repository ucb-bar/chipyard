##############################################################
# extra variables/targets ingested by the chipyard make system
##############################################################

DROMAJO_DIR = $(base_dir)/tools/dromajo/dromajo-src/src
DROMAJO_LIB_NAME = dromajo_cosim
DROMAJO_LIB = $(DROMAJO_DIR)/lib$(DROMAJO_LIB_NAME).a

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

# depending on where the simulation is done, use the auto-variable or the hardcoded defined one
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

# simargs needed (i.e. like +drj_test=hello)
EXTRA_SIM_FLAGS += $(DROMAJO_FLAGS)

# extra vcs compile flags
EXTRA_VCS_FLAGS += -CC "-I$(DROMAJO_DIR)" $(DROMAJO_LIB)

# extra verilator compile flags
EXTRA_VERILATOR_FLAGS += -CFLAGS "-I$(DROMAJO_DIR)" -LDFLAGS "-L$(DROMAJO_DIR) -Wl,-rpath,$(DROMAJO_DIR) -l$(DROMAJO_LIB_NAME)"

# extra simulation sources needed for VCS/Verilator compile
EXTRA_SIM_SOURCES += $(DROMAJO_PARAMS_SYMLINK) $(DROMAJO_LIB)
