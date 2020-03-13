########################################################################
# CS152 Lab 3: Open-Ended Problem 2                                    #
########################################################################

CC := riscv64-unknown-elf-gcc
OBJDUMP := riscv64-unknown-elf-objdump

CFLAGS := -O2 -std=gnu11 -Wall

.PHONY: all
all: spectre.dump

.PRECIOUS: %.riscv
%.riscv: %.c $(wildcard *.h)
	$(CC) $(CFLAGS) -o $@ $<

%.dump: %.riscv
	$(OBJDUMP) -d $< > $@

.PHONY: clean
clean:
	rm -f -- *.riscv *.o *.dump

.SUFFIXES: # Disable built-in suffix rules
