base_dir=$(abspath ..)
sim_dir=$(abspath .)

PROJECT ?= example
MODEL ?= TestHarness
CONFIG ?= DefaultExampleConfig
CFG_PROJECT ?= $(PROJECT)
TB ?= TestDriver

sim = $(sim_dir)/simulator-$(PROJECT)-$(CONFIG)
sim_debug = $(sim_dir)/simulator-$(PROJECT)-$(CONFIG)-debug

default: $(sim)

debug: $(sim_debug)

CXXFLAGS := $(CXXFLAGS) -O1 -std=c++11 -I$(RISCV)/include -D__STDC_FORMAT_MACROS
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(sim_dir) -lfesvr -lpthread

include $(base_dir)/Makefrag
include $(sim_dir)/Makefrag-verilator

long_name = $(PROJECT).$(MODEL).$(CONFIG)

sim_vsrcs = \
	$(build_dir)/$(long_name).v \
	$(base_dir)/rocket-chip/vsrc/AsyncResetReg.v \
	$(base_dir)/testchipip/vsrc/SimSerial.v \
	$(base_dir)/testchipip/vsrc/SimBlockDevice.v \

sim_csrcs = \
	$(base_dir)/testchipip/csrc/SimSerial.cc \
	$(base_dir)/testchipip/csrc/SimBlockDevice.cc \
	$(base_dir)/testchipip/csrc/blkdev.cc \
	$(base_dir)/testchipip/csrc/verilator-harness.cc

model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

model_header = $(model_dir)/V$(MODEL).h
model_header_debug = $(model_dir_debug)/V$(MODEL).h

model_mk = $(model_dir)/V$(MODEL).mk
model_mk_debug = $(model_dir_debug)/V$(MODEL).mk

$(model_mk): $(sim_vsrcs) $(INSTALLED_VERILATOR)
	rm -rf $(build_dir)/$(long_name)
	mkdir -p $(build_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(build_dir)/$(long_name) \
	-o $(sim) $< $(sim_csrcs) -LDFLAGS "$(LDFLAGS)" \
	-CFLAGS "-I$(build_dir) -include $(model_header)"
	touch $@

$(sim): $(model_mk) $(sim_csrcs)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(build_dir)/$(long_name) -f V$(MODEL).mk


$(model_mk_debug): $(sim_vsrcs) $(INSTALLED_VERILATOR)
	mkdir -p $(build_dir)/$(long_name).debug
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(build_dir)/$(long_name).debug --trace \
	-o $(sim_debug) $< $(sim_csrcs) -LDFLAGS "$(LDFLAGS)" \
	-CFLAGS "-I$(build_dir) -include $(model_header_debug)"
	touch $@

$(sim_debug): $(model_mk_debug) $(sim_csrcs)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(build_dir)/$(long_name).debug -f V$(MODEL).mk

clean:
	rm -rf generated-src ./simulator-*
