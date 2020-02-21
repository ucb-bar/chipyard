########################################################################
# CS152 Lab 2: Open-Ended Problem 4.2
########################################################################

SHELL := /bin/bash

ifndef RISCV
$(error source ~cs152/sp21/cs152.lab2.bashrc)
endif

# Undefine to run in pk (required for file I/O)
BAREMETAL ?= 1

bmarks_dir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
bmarks_dir := $(bmarks_dir:/=)

CXX     := riscv64-unknown-elf-g++
OBJDUMP := riscv64-unknown-elf-objdump

CXXFLAGS := -Wno-unknown-pragmas
ifneq ($(BAREMETAL),)
CXXFLAGS += -specs=htif.specs -specs=htif_argv.specs -Wl,--defsym=__heap_size=8M
endif

.PHONY: gapbs
gapbs:

gapbs_dir := $(bmarks_dir)/gapbs
gapbs_conv := $(gapbs_dir)/converter
gapbs_kernels := bfs cc sssp tc
gapbs_bin := $(addprefix $(gapbs_dir)/,$(gapbs_kernels))
gapbs_dump := $(addsuffix .dump, $(gapbs_kernels))
gapbs_graph := kron10.sg

$(gapbs_bin): %: $(gapbs_dir)/Makefile
	CXX=$(CXX) CXX_FLAGS='$(CXXFLAGS)' $(MAKE) -C $(gapbs_dir) SERIAL=1 $@

$(gapbs_dump): %.dump: $(gapbs_dir)/%
	$(OBJDUMP) -d -C $< > $@

$(gapbs_conv): $(gapbs_dir)/Makefile
	$(MAKE) -C $(gapbs_dir) $(notdir $@)

$(gapbs_graph): $(gapbs_conv)
	$< -g 10 -b $@

.PHONY: gapbs
gapbs: $(gapbs_dump) $(gapbs_graph)

.PHONY: clean
clean:
	$(MAKE) -C $(gapbs_dir) clean
	rm -rf -- $(gapbs_dump) $(gapbs_graph)

.SUFFIXES: # Disable built-in suffix rules
