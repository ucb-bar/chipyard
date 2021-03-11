########################################################################
# CS152 Lab 3: Open-Ended Problem 2                                    #
########################################################################

workdir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
workdir := $(workdir:/=)
basedir := $(workdir)/../..


CC := riscv64-unknown-elf-gcc
OBJDUMP := riscv64-unknown-elf-objdump

CFLAGS := -O2 -std=gnu11 -Wall

.PHONY: all
all: spectre.dump baremetal.dump

spectre.riscv: CFLAGS += -specs=nano.specs
baremetal.riscv: CFLAGS += -specs=htif_nano.specs

.PRECIOUS: %.riscv
%.riscv: %.c $(wildcard *.h)
	$(CC) $(CFLAGS) -o $@ $<

%.dump: %.riscv
	$(OBJDUMP) -d $< > $@

.PHONY: clean
clean:
	rm -f -- *.riscv *.o *.dump

# Autograder submission

zip_obj := upload.zip
zip_srcs := $(wildcard Makefile *.c *.h *.S)

.PHONY: zip
zip: $(zip_srcs)
	rm -f -- $(zip_obj)
	zip -j $(zip_obj) $^


.SUFFIXES: # Disable built-in suffix rules
