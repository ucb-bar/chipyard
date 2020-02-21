########################################################################
# CS152 Lab 2: Open-Ended Problem 4.2                                  #
########################################################################

SHELL := /bin/bash

bmarks_dir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
bmarks_dir := $(bmarks_dir:/=)

CXX     := riscv64-unknown-elf-g++
STRIP   := riscv64-unknown-elf-strip
OBJDUMP := riscv64-unknown-elf-objdump

.PHONY: gapbs
gapbs:

gapbs_dir := $(bmarks_dir)/gapbs
gapbs_conv := $(gapbs_dir)/converter
gapbs_kernels := bfs cc sssp tc
gapbs_graph := kron10.sg

$(gapbs_kernels): %: $(gapbs_dir)/Makefile
	CXX=$(CXX) CXX_FLAGS='-Wno-unknown-pragmas' $(MAKE) -C $(gapbs_dir) SERIAL=1 $@
	$(STRIP) -o $@ $(gapbs_dir)/$@
	$(OBJDUMP) -d $(gapbs_dir)/$@ > $@.dump

$(gapbs_conv): $(gapbs_dir)/Makefile
	$(MAKE) -C $(gapbs_dir) $(notdir $@)

$(gapbs_graph): $(gapbs_conv)
	$< -g 10 -b $@

.PHONY: gapbs
gapbs: $(gapbs_kernels) $(gapbs_graph)

.PHONY: clean
clean:
	$(MAKE) -C $(gapbs_dir) clean
	rm -f -- $(gapbs_kernels)

.SUFFIXES: # Disable built-in suffix rules
