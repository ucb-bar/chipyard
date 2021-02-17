########################################################################
# CS152 Lab 2: Open-Ended Problem 4.1
########################################################################

# Add physical environment tests to this list
bmarks_p := bmark-p
# Add virtual memory environment tests to this list
bmarks_v := bmark-v

ifndef RISCV
$(error source ~cs152/sp21/cs152.lab2.bashrc)
endif

CC      := riscv64-unknown-elf-gcc
OBJDUMP := riscv64-unknown-elf-objdump

CFLAGS := -O2 -std=gnu11 -Wall

progs_p := $(addsuffix .riscv,$(bmarks_p))
progs_v := $(addsuffix .riscv,$(bmarks_v))
srcs :=

$(progs_p): CFLAGS += -specs=htif.specs -specs=htif_argv.specs -Wl,--defsym=__heap_size=8M
$(progs_p): srcs += trap.c

.PHONY: all
all: $(addsuffix .dump,$(bmarks_p) $(bmarks_v))

.PRECIOUS: %.riscv
%.riscv: %.c $(srcs)
	$(CC) $(CFLAGS) -o $@ $< $(srcs)

%.dump: %.riscv
	$(OBJDUMP) -d $< > $@

.PHONY: clean
clean:
	rm -f -- *.riscv *.o *.dump

.SUFFIXES: # Disable built-in suffix rules
