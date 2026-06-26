# Makefile for TMA performance counter tests
# Uses a minimal bare-metal startup compatible with chipyard's TSI/HTIF.

CHIPYARD_DIR ?= $(abspath ../..)
RISCV_TOOLS ?= $(CHIPYARD_DIR)/.conda-env/riscv-tools

RISCV_PREFIX ?= $(RISCV_TOOLS)/bin/riscv64-unknown-elf-
CC = $(RISCV_PREFIX)gcc
OBJDUMP = $(RISCV_PREFIX)objdump

CFLAGS = -march=rv64gc -mabi=lp64d -mcmodel=medany -O2 -static -std=gnu99 \
         -fno-common -fno-builtin-printf -I.

LDFLAGS = -static -nostdlib -nostartfiles -lgcc \
          -T $(CHIPYARD_DIR)/tests/htif.ld

TESTS = test_simple test_noop test_tma_basic test_tma_slot_sum \
        test_bad_speculation test_frontend_bound test_backend_bound \
        test_memory_bound test_instruction_mix test_stall_reasons \
        test_bpd_source test_mmio_counters test_retired_counts

all: $(addsuffix .riscv, $(TESTS))

%.riscv: %.c tma_counters.h crt_tma.S
	$(CC) $(CFLAGS) $(LDFLAGS) crt_tma.S $< -o $@
	$(OBJDUMP) -d $@ > $@.dump

clean:
	rm -f *.riscv *.dump

.PHONY: all clean