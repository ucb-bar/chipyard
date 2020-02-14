########################################################################
# CS152 Lab 2: Open-Ended Problem 4.3
########################################################################

SHELL := /bin/sh

ifndef RISCV
$(error source ~cs152/sp21/cs152.lab2.bashrc)
endif

base_dir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
base_dir := $(base_dir:/=)

.PHONY: all
all: run

spike_dir := $(base_dir)/riscv-isa-sim
spike := $(spike_dir)/spike
pk := $(RISCV)/riscv64-unknown-elf/bin/pk

$(spike_dir)/Makefile: $(spike_dir)/configure
	cd $(dir $@) && ./configure --prefix=$(base_dir)/install

.PHONY: spike
spike: $(spike_dir)/Makefile
	$(MAKE) -C $(spike_dir) spike

.PHONY: clean
clean:: $(spike_dir)/Makefile
	$(MAKE) -C $(spike_dir) clean

include $(base_dir)/cache.mk

TARGET := riscv
TARGET_RUN := $(spike) --dc=$(L1D_SETS):$(L1D_WAYS):$(L1D_LINESIZE) --ic=$(L1I_SETS):$(L1I_WAYS):$(L1I_LINESIZE) $(pk)
TARGET_REDIRECT := | tee
TARGET_DEPENDS := spike

include $(base_dir)/CPU2006/spec.mk

.SUFFIXES: # Disable built-in rules
