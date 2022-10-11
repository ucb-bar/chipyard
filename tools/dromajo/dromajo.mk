##############################################################
# extra variables/targets ingested by the chipyard make system
##############################################################

DROMAJO_DIR = $(base_dir)/tools/dromajo/dromajo-src/src
DROMAJO_LIB_NAME = dromajo_cosim
DROMAJO_LIB = $(CONDA_PREFIX)/lib/lib$(DROMAJO_LIB_NAME).a

# Dromajo assumes using the default bootrom
DROMAJO_ROM = $(build_dir)/bootrom.rv64.img

DTS_FILE = $(build_dir)/$(long_name).dts
DROMAJO_DTB = $(build_dir)/$(long_name).dtb

$(DTS_FILE): $(FIRRTL_FILE)

$(DROMAJO_DTB): $(DTS_FILE)
	dtc -I dts -O dtb -o $(DROMAJO_DTB) $(DTS_FILE)

DROMAJO_SRCS = $(call lookup_srcs,$(DROMAJO_DIR),cc) $(call lookup_srcs,$(DROMAJO_DIR),h)

$(DROMAJO_LIB): $(DROMAJO_SRCS)
	$(MAKE) -C $(DROMAJO_DIR)

# depending on where the simulation is done, use the auto-variable or the hardcoded defined one
ifeq ($(BINARY),)
DROMAJO_BIN = $(<)
else
DROMAJO_BIN = $(BINARY)
endif

DROMAJO_FLAGS = +drj_dtb=$(DROMAJO_DTB) +drj_rom=$(DROMAJO_ROM) +drj_bin=$(DROMAJO_BIN)

DROMAJO_PARAMS_FILE    = $(build_dir)/$(long_name).dromajo_params.h
DROMAJO_PARAMS_SYMLINK = $(build_dir)/dromajo_params.h

$(DROMAJO_PARAMS_FILE): $(FIRRTL_FILE)

$(DROMAJO_PARAMS_SYMLINK): $(DROMAJO_PARAMS_FILE)
	rm -rf $(DROMAJO_PARAMS_SYMLINK)
	ln -s $(DROMAJO_PARAMS_FILE) $(DROMAJO_PARAMS_SYMLINK)

##################################################################
# THE FOLLOWING MUST BE += operators
##################################################################

# simargs needed (i.e. like +drj_test=hello)
ifdef ENABLE_DROMAJO
EXTRA_SIM_FLAGS += $(DROMAJO_FLAGS)

# CC flags needed for all simulations
EXTRA_SIM_CXXFLAGS += -I$(DROMAJO_DIR)

# sourced needed for simulation
EXTRA_SIM_SOURCES += $(DROMAJO_LIB)

# requirements needed for simulation
EXTRA_SIM_REQS += $(DROMAJO_PARAMS_SYMLINK) $(DROMAJO_LIB) $(DROMAJO_DTB)
endif
