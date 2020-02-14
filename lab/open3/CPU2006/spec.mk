.DELETE_ON_ERROR:

spec_dir := $(dir $(realpath $(lastword $(MAKEFILE_LIST))))
spec_dir := $(spec_dir:/=)

CC := riscv64-unknown-elf-gcc
CXX := riscv64-unknown-elf-g++

CFLAGS := -O2 -DSPEC_CPU -DSPEC_CPU_LP64
CXXFLAGS := $(CFLAGS)

bmarks := \
	401.bzip2 \
	429.mcf \
	450.soplex \
	458.sjeng \
	470.lbm

TARGET ?= riscv
TARGET_RUN ?= spike pk
TARGET_REDIRECT ?= >
TARGET_DEPENDS ?=

build_dir := $(spec_dir)/build.$(TARGET)

$(build_dir):
	mkdir -p $@

include $(patsubst %,$(spec_dir)/%/src/bmark.mk,$(bmarks))

bmarks_bin := $(addprefix $(build_dir)/,$(bmarks))
bmarks_out := $(addsuffix .out,$(bmarks_bin))

.PHONY: spec
spec: $(bmarks_bin)

.PHONY: run
run: $(bmarks_out)

.PHONY: clean-run
clean-run:
	rm -f -- $(bmarks_out)

.PHONY: clean
clean:: clean-run
