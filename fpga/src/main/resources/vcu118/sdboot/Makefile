# RISCV environment variable must be set
ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
BUILD_DIR := $(ROOT_DIR)/build

CC=$(RISCV)/bin/riscv64-unknown-elf-gcc
OBJCOPY=$(RISCV)/bin/riscv64-unknown-elf-objcopy
OBJDUMP=$(RISCV)/bin/riscv64-unknown-elf-objdump
CFLAGS=-march=rv64ima_zicsr_zifencei -mcmodel=medany -O2 -std=gnu11 -Wall -nostartfiles
CFLAGS+= -fno-common -g -DENTROPY=0 -mabi=lp64 -DNONSMP_HART=0
CFLAGS+= -I $(ROOT_DIR)/include -I.
LFLAGS=-static -nostdlib -L $(ROOT_DIR)/linker -T sdboot.elf.lds

# default to 1MHz but really should be overridden
PBUS_CLK ?= 1000000

# SPI divisors set by the design rather than derived from PBUS_CLK, when its SPI
# controller does not run at the peripheral bus clock
ifneq ($(SPI_DIV),)
CFLAGS += -DSPI_DIV=$(SPI_DIV)
endif
ifneq ($(SPI_INIT_DIV),)
CFLAGS += -DSPI_INIT_DIV=$(SPI_INIT_DIV)
endif

default: elf bin dump

# Always rebuilt: the image depends on PBUS_CLK as well as the sources, and the
# build takes a fraction of a second, so a cached image built for another clock
# (or, through the vc707 and zcu104 symlinks, another board) is never reused.
elf := $(BUILD_DIR)/sdboot.elf
$(elf): head.S kprintf.c sd.c FORCE
	mkdir -p $(BUILD_DIR)
	$(CC) $(CFLAGS) -DTL_CLK="$(PBUS_CLK)UL" $(LFLAGS) -o $@ head.S sd.c kprintf.c

.PHONY: elf
elf: $(elf)

bin := $(BUILD_DIR)/sdboot.bin
$(bin): $(elf)
	mkdir -p $(BUILD_DIR)
	$(OBJCOPY) -O binary --change-addresses=-0x10000 $< $@

.PHONY: bin
bin: $(bin)

dump := $(BUILD_DIR)/sdboot.dump
$(dump): $(elf)
	$(OBJDUMP) -D -S $< > $@

.PHONY: dump
dump: $(dump)

.PHONY: FORCE
FORCE:

.PHONY: clean
clean::
	rm -rf $(BUILD_DIR)
