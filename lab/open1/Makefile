########################################################################
# CS152 Lab 2: Open-Ended Problem 4.1
########################################################################

ifndef RISCV
$(error source ~cs152/sp21/cs152.lab2.bashrc)
endif

SHELL := /bin/bash

base_dir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
base_dir := $(base_dir:/=)

CONFIG ?= CS152RocketMysteryConfig
MODEL_PACKAGE ?= chipyard

.PHONY: ccbench-sweep
ccbench-sweep:

ccbench_dir := $(base_dir)/ccbench/caches

# Array sizes (4 byte words)
ccbench_sizes := 262144 131072 65536 49152 32768 24576 16384 12288 8192 6144 4096 2048 1024
# Number of iterations
ccbench_iters := 10000
# Cache line stride
ccbench_runtype := 0

ccbench_LDFLAGS := -specs=htif.specs -specs=htif_argv.specs -Wl,--defsym=__heap_size=4M

# Build the `caches` micro-benchmark
ccbench_bin := $(ccbench_dir)/caches

.PHONY: ccbench-caches
$(ccbench_bin) ccbench-caches:
	$(MAKE) -C $(ccbench_dir) ARCH=riscv LD_FLAGS='$(ccbench_LDFLAGS)' caches

output_dir := $(base_dir)/output/$(CONFIG)

$(output_dir):
	mkdir -p $@

# Build simulator preemptively for parallel simulations
sim_dir := $(base_dir)/../../sims/verilator
sim_prefix := simulator
sim := $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)

.PHONY: sim
$(sim) sim:
	$(MAKE) -C $(sim_dir) CONFIG=$(CONFIG)

# Parameter $(1) is array size
define ccbench_run
$$(output_dir)/ccbench-$(1).log: $$(ccbench_bin) $$(sim) | $$(output_dir)
	$$(MAKE) -C $$(sim_dir) CONFIG=$$(CONFIG) \
		BINARY='$$< $(1) $$(ccbench_iters) $$(ccbench_runtype)' \
		run-binary-hex | tee $$@
endef

# Generate simulation rules for each size
$(foreach size,$(ccbench_sizes),$(eval $(call ccbench_run,$(size))))

ccbench_logs := $(patsubst %,$(output_dir)/ccbench-%.log,$(ccbench_sizes))
ccbench-sweep: $(ccbench_logs)


ccbench_report := $(ccbench_dir)/reports/report-$(CONFIG).txt
ccbench_plot := $(ccbench_dir)/plots/plot-$(CONFIG).pdf

# Generate consolidated report file
$(ccbench_report): $(ccbench_logs)
	mkdir -p $(dir $@)
	{ \
		echo 'NumDataPointsPerSet:[$(words $^)]' && \
		cd '$(output_dir)' && \
		grep -F -h 'App:[caches]' $(shell echo $(notdir $^) | sort -n -u) ; \
	} > $@

# Generate plot
$(ccbench_plot): $(ccbench_report)
	cd $(ccbench_dir) && ./run_test.py -n -r '$(notdir $<)' -o '$(notdir $@)'

.PHONY: ccbench-plot
ccbench-plot: $(ccbench_plot)


.PHONY: clean
clean:
	$(MAKE) -C $(ccbench_dir) ARCH=riscv clean
	rm -f -- $(ccbench_logs)

.SUFFIXES: # Disable built-in suffix rules
.DELETE_ON_ERROR:
