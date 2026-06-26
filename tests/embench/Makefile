RISCV_PREFIX ?= riscv64-unknown-elf-
CC = $(RISCV_PREFIX)gcc
OBJDUMP = $(RISCV_PREFIX)objdump

EMBENCH       = ../embench-src
BENCH_COMMON  = ../../toolchains/riscv-tools/riscv-tests/benchmarks/common
ENV_DIR       = ../../toolchains/riscv-tools/riscv-tests/env
TMA_DIR       = ../tmatests

# Startup and stubs
STARTUP_SRCS = \
	$(BENCH_COMMON)/crt.S \
	baremetal_init.c \
	baremetal_stubs.c

# Embench support sources
SUPPORT_SRCS = \
	$(EMBENCH)/support/main.c \
	$(EMBENCH)/support/beebsc.c \
	boardsupport.c

INCLUDES = \
	-I$(EMBENCH)/support \
	-I$(TMA_DIR) \
	-I$(ENV_DIR) \
	-I$(BENCH_COMMON) \
	-I.

CFLAGS = -O2 -mcmodel=medany -static -std=gnu99 -march=rv64gc -mabi=lp64d \
	-fno-common -fno-builtin-printf \
	-DWARMUP_HEAT=1 \
	-DGLOBAL_SCALE_FACTOR=1 \
	-DHAVE_CONFIG_H=1 \
	-DHAVE_BOARDSUPPORT_H=1 \
	$(INCLUDES)

LDFLAGS_PRE = -static -nostartfiles \
	-T $(BENCH_COMMON)/test.ld
LDFLAGS_POST = -lm -lc -lgcc

# Benchmark list
BENCHMARKS = aha-mont64 crc32 depthconv edn huffbench matmult-int md5sum \
	nettle-aes nettle-sha256 nsichneu picojpeg \
	qrduino sglib-combined slre statemate \
	tarfind ud wikisort xgboost

# Template: $(1) = benchmark name
define BUILD_BENCH
$(1).riscv: $$(STARTUP_SRCS) $$(SUPPORT_SRCS) $$(wildcard $(EMBENCH)/src/$(1)/*.c)
	$$(CC) $$(CFLAGS) -I$(EMBENCH)/src/$(1) $$(LDFLAGS_PRE) \
		$$(STARTUP_SRCS) $$(SUPPORT_SRCS) \
		$$(wildcard $(EMBENCH)/src/$(1)/*.c) \
		$$(LDFLAGS_POST) -o $$@
	$$(OBJDUMP) -d $$@ > $$@.dump
endef

$(foreach b,$(BENCHMARKS),$(eval $(call BUILD_BENCH,$(b))))

ALL_BENCHMARKS = $(addsuffix .riscv,$(BENCHMARKS))

.PHONY: all clean run-all

all: $(ALL_BENCHMARKS)

clean:
	rm -f *.riscv *.dump

# Run all benchmarks on Verilator with TMA
SIM_DIR = ../../sims/verilator
CONFIG ?= MegaBoomVerilatorConfig

run-all: $(ALL_BENCHMARKS)
	@for b in $(ALL_BENCHMARKS); do \
		echo "=== Running $$b ==="; \
		$(MAKE) -C $(SIM_DIR) run-binary \
			CONFIG=$(CONFIG) BINARY=$(abspath $$b) DUMP_TMA=1 LOADMEM=1; \
	done
