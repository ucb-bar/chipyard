########################################################################
# CS152 Lab 2: Directed Portion                                        #
########################################################################

CC := riscv64-unknown-elf-gcc
OBJDUMP := riscv64-unknown-elf-objdump

CFLAGS := -O2 -std=gnu11 -Wall -specs=htif_nano.specs

.PHONY: all
all: transpose.dump

.PRECIOUS: %.riscv
%.riscv: %.c trap.c $(wildcard *.h)
	$(CC) $(CFLAGS) -o $@ $< trap.c

%.dump: %.riscv
	$(OBJDUMP) -d $< > $@

.PHONY: clean
clean:
	rm -f -- *.riscv *.o *.dump

.SUFFIXES: # Disable built-in suffix rules
