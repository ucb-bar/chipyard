########################################################################
# CS152 Lab 2: Open-Ended Problem 4.1                                  #
########################################################################

SHELL := /bin/bash

base_dir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
base_dir := $(base_dir:/=)

TARGET_CONFIG ?= CS152RocketChipMysteryConfig

SIMDIR := $(base_dir)/../../sims/firesim/sim

.PHONY: ccbench-sweep
ccbench-sweep:

ccbench_dir := $(base_dir)/ccbench/caches

# Array sizes (4 byte words)
ccbench_sizes := 1024 2048 4096 8192 12288 16384 24576 32768 65536 131072 262144
# Number of iterations
ccbench_iters := 10000
# Cache line stride
ccbench_runtype := 0

# Build the `caches` micro-benchmark
.PHONY: ccbench-caches
ccbench-caches:
	$(MAKE) -C $(ccbench_dir) ARCH=riscv caches

output_dir := $(base_dir)/output/$(TARGET_CONFIG)

$(output_dir):
	mkdir -p $@

EMUL ?= verilator
export EMUL

# Build simulator preemptively
.PHONY: $(EMUL)
$(EMUL):
	$(MAKE) -C $(SIMDIR) TARGET_CONFIG=$(TARGET_CONFIG) $@

# Parameter $(1) is array size
define ccbench_run
$$(output_dir)/ccbench-$(1).out: ccbench-caches $(EMUL) | $$(output_dir)
	$$(MAKE) -C $$(SIMDIR) \
		TARGET_CONFIG=$$(TARGET_CONFIG) \
		CCBENCH_ARGS='$(1) $$(ccbench_iters) $$(ccbench_runtype)' \
		run-ccbench 2> >(tee $$@)
endef

# Generate simulation rules for each size
$(foreach size,$(ccbench_sizes),$(eval $(call ccbench_run,$(size))))

ccbench_logs := $(patsubst %,$(output_dir)/ccbench-%.out,$(ccbench_sizes))
ccbench-sweep: $(ccbench_logs)


ccbench_report := $(ccbench_dir)/reports/report-$(TARGET_CONFIG).txt
ccbench_plot := $(ccbench_dir)/plots/plot-$(TARGET_CONFIG).pdf

# Generate consolidated report file
$(ccbench_report): $(ccbench_logs)
	{ \
		echo 'NumDataPointsPerSet:[$(words $^)]' && \
		cd '$(output_dir)' && \
		grep -F -n 'App:[caches]' $(shell echo $(notdir $^) | sort -n -u) ; \
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
