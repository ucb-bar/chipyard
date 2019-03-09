#########################################################################################
# verilator makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# name of simulator (used to generate *.f arguments file)
#########################################################################################
sim_name = verilator

#########################################################################################
# vcs simulator types and rules
#########################################################################################
sim_prefix = simulator
sim = $(sim_dir)/$(sim_prefix)-$(PROJECT)-$(CONFIG)
sim_debug = $(sim_dir)/$(sim_prefix)-$(PROJECT)-$(CONFIG)-debug

.PHONY: default debug
default: $(sim)
debug: $(sim_debug)

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk
include $(sim_dir)/verilator.mk

#########################################################################################
# verilator build paths and file names
#########################################################################################
model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

model_header = $(model_dir)/V$(MODEL).h
model_header_debug = $(model_dir_debug)/V$(MODEL).h

model_mk = $(model_dir)/V$(MODEL).mk
model_mk_debug = $(model_dir_debug)/V$(MODEL).mk

#########################################################################################
# build makefile fragment that builds the verilator sim rules
#########################################################################################
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(sim_dir) -lfesvr -lpthread

$(model_mk): $(sim_vsrcs) $(sim_dotf) $(INSTALLED_VERILATOR)
	rm -rf $(build_dir)/$(long_name)
	mkdir -p $(build_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(build_dir)/$(long_name) \
	-o $(sim) $(sim_vsrcs) -f $(sim_dotf) -f $(sim_blackboxes) -LDFLAGS "$(LDFLAGS)" \
	-CFLAGS "-I$(build_dir) -include $(build_dir)/$(long_name).plusArgs -include $(model_header)"
	touch $@

$(model_mk_debug): $(sim_vsrcs) $(sim_dotf) $(INSTALLED_VERILATOR)
	rm -rf $(build_dir)/$(long_name)
	mkdir -p $(build_dir)/$(long_name).debug
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(build_dir)/$(long_name).debug --trace \
	-o $(sim_debug) $(sim_vsrcs) -f $(sim_dotf) -f $(sim_blackboxes) -LDFLAGS "$(LDFLAGS)" \
	-CFLAGS "-I$(build_dir) -include $(build_dir)/$(long_name).plusArgs -include $(model_header_debug)"
	touch $@

#########################################################################################
# invoke make to make verilator sim rules
#########################################################################################
$(sim): $(model_mk)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(build_dir)/$(long_name) -f V$(MODEL).mk

$(sim_debug): $(model_mk_debug)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(build_dir)/$(long_name).debug -f V$(MODEL).mk

#########################################################################################
# create a vcs vpd rule
#########################################################################################
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	$(sim_debug) -v$@.vcd +max-cycles=$(timeout_cycles) $<

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean: clean-scala
	rm -rf $(build_dir) $(sim_prefix)-*
