CROSS ?= riscv64-unknown-elf-

# Bare-metal (htif_nano.specs) rv64imafd with the `fence` the probes rely on.
# Overrides: EXTRA_CFLAGS='-DATTACK_BYTES=4 -DTRAIN_TIMES=70 -DROUNDS=3'
CFLAGS ?= -march=rv64imafd -mabi=lp64d -mcmodel=medany -std=gnu99 -O2 \
          -Wall -Wextra -specs=htif_nano.specs

CFLAGS += $(EXTRA_CFLAGS)

TARGETS = v1.riscv v2.riscv rsb.riscv

all: $(TARGETS)

%.riscv: %.c spectre.c spectre.h
	$(CROSS)gcc $(CFLAGS) -o $@ $< spectre.c

rsb.riscv: rsb.c rsb.S spectre.c spectre.h
	$(CROSS)gcc $(CFLAGS) -o $@ rsb.c rsb.S spectre.c

# Verdict-logic self-checks with a mock simulator; no RTL simulation.
test:
	./test-runner.sh

clean:
	rm -f $(TARGETS)

.PHONY: all clean test
