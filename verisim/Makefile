base_dir=$(abspath ..)
sim_dir=$(abspath .)
testchip_dir=$(base_dir)/testchipip

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
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(sim_dir) -L$(testchip_dir)/DRAMSim2 -lfesvr -lpthread -ldramsim

include $(base_dir)/Makefrag
include $(testchip_dir)/Makefrag

# Run Verilator to produce a fast binary to emulate this circuit.
VERILATOR := verilator --cc --exe
VERILATOR_FLAGS := --top-module $(MODEL) \
  +define+PRINTF_COND=\$$c\(\"verbose\",\"\&\&\"\,\"done_reset\"\) \
  +define+STOP_COND=\$$c\(\"done_reset\"\) --assert \
  --output-split 20000 \
	-Wno-STMTDLY --x-assign unique \
  -I$(base_dir)/testchipip/vsrc -I$(base_dir)/rocket-chip/vsrc \
  -O3 -CFLAGS "$(CXXFLAGS) -DVERILATOR -include $(base_dir)/rocket-chip/csrc/verilator.h"

long_name = $(PROJECT).$(MODEL).$(CONFIG)

sim_vsrcs = \
	$(build_dir)/$(long_name).v \
	$(base_dir)/rocket-chip/vsrc/AsyncResetReg.v \
	$(testchip_vsrcs)

sim_csrcs = \
	$(base_dir)/testchipip/csrc/verilator-harness.cc \
	$(testchip_csrcs)

model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

model_header = $(model_dir)/V$(MODEL).h
model_header_debug = $(model_dir_debug)/V$(MODEL).h

model_mk = $(model_dir)/V$(MODEL).mk
model_mk_debug = $(model_dir_debug)/V$(MODEL).mk

$(model_mk): $(sim_vsrcs)
	rm -rf $(build_dir)/$(long_name)
	mkdir -p $(build_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(build_dir)/$(long_name) \
	-o $(sim) $< $(sim_csrcs) -LDFLAGS "$(LDFLAGS)" \
	-CFLAGS "-I$(build_dir) $(testchip_includes) -include $(model_header)"
	touch $@

$(sim): $(model_mk) $(sim_csrcs)
	$(MAKE) -j4 VM_PARALLEL_BUILDS=1 -C $(build_dir)/$(long_name) -f V$(MODEL).mk


$(model_mk_debug): $(sim_vsrcs)
	mkdir -p $(build_dir)/$(long_name).debug
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(build_dir)/$(long_name).debug --trace \
	-o $(sim_debug) $< $(sim_csrcs) -LDFLAGS "$(LDFLAGS)" \
	-CFLAGS "-I$(build_dir) $(testchip_includes) -include $(model_header_debug)"
	touch $@

$(sim_debug): $(model_mk_debug) $(sim_csrcs)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(build_dir)/$(long_name).debug -f V$(MODEL).mk

$(output_dir)/%.out: $(output_dir)/% $(sim)
	$(sim) +verbose +max-cycles=1000000 $< 3>&1 1>&2 2>&3 | spike-dasm > $@

$(output_dir)/%.run: $(output_dir)/% $(sim)
	$(sim) +max-cycles=1000000 $< && touch $@

$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	$(sim_debug) -v$@.vcd +max-cycles=1000000 $<

$(output_dir)/%.riscv.out: $(bmarks_dir)/%.riscv $(sim) $(output_dir)/.dummy
	$(sim) +dramsim +max-cycles=1000000 $< > $@

run-regression-tests: $(addprefix $(output_dir)/,$(addsuffix .out,$(regression-tests)))

run-regression-tests-fast: $(addprefix $(output_dir)/,$(addsuffix .run,$(regression-tests)))

run-regression-tests-debug: $(addprefix $(output_dir)/,$(addsuffix .vpd,$(regression-tests)))

run-benchmarks: $(addprefix $(output_dir)/,$(addsuffix .riscv.out,$(bmarks)))

clean:
	rm -rf generated-src ./simulator-*
