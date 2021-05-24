# RISCV environment variable must be set

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
BUILD_DIR := $(ROOT_DIR)/build

CC=$(RISCV)/bin/riscv64-unknown-elf-gcc
OBJCOPY=$(RISCV)/bin/riscv64-unknown-elf-objcopy
OBJDUMP=$(RISCV)/bin/riscv64-unknown-elf-objdump
CFLAGS=-march=rv32imac -mabi=ilp32 -O2 -std=gnu11 -Wall -I. -nostartfiles -fno-common -g
LFLAGS=-static -nostdlib

PBUS_CLK ?= 1000000

default: elf bin dump

elf := $(BUILD_DIR)/xip.elf
$(elf): xip.S
	mkdir -p $(BUILD_DIR)
	$(CC) $(CFLAGS) -DTL_CLK="$(PBUS_CLK)UL" -DXIP_TARGET_ADDR=0x20400000 $(LFLAGS) -o $@ $<

.PHONY: elf
elf: $(elf)

bin := $(BUILD_DIR)/xip.bin
$(bin): $(elf)
	mkdir -p $(BUILD_DIR)
	$(OBJCOPY) -O binary $< $@

.PHONY: bin
bin: $(bin)

dump := $(BUILD_DIR)/xip.dump
$(dump): $(elf)
	$(OBJDUMP) -D -S $< > $@

.PHONY: dump
dump: $(dump)

.PHONY: clean
clean::
	rm -rf $(BUILD_DIR)
