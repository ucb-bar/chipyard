########################################################################
# CS152 Lab 5
########################################################################

srcdir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
srcdir := $(srcdir:/=)

CC := riscv64-unknown-elf-gcc
OBJDUMP := riscv64-unknown-elf-objdump
RISCV_SIM := spike -p2 --isa=rv64gc -l

CFLAGS := -O2 -Wall -Wno-unused-function -pedantic -std=c11 -I $(srcdir)/common -specs=htif_nano.specs
LDFLAGS := -Wl,--defsym=__main=main -T $(srcdir)/common/data.ld

bmarks := \
	mt-vvadd-naive \
	mt-vvadd-opt \
	mt-matmul-naive \
	mt-matmul-opt

.PHONY: all
all: dump

define compile_template
$(1).riscv: $(wildcard $(srcdir)/$(1)/*) $(wildcard $(srcdir)/common/*)
	$$(CC) $$(CFLAGS) -o $$@ $(wildcard $(srcdir)/$(1)/*.c) $(wildcard $(srcdir)/common/*.c) $(wildcard $(srcdir)/common/*.S) $$(LDFLAGS)
endef

$(foreach bmark,$(bmarks),$(eval $(call compile_template,$(bmark))))

bmarks_riscv_bin  := $(addsuffix .riscv,$(bmarks))
bmarks_riscv_dump := $(addsuffix .riscv.dump,$(bmarks))
bmarks_riscv_out  := $(addsuffix .riscv.out,$(bmarks))
bmarks_riscv_log  := $(addsuffix .riscv.log,$(bmarks))

$(bmarks_riscv_dump): %.riscv.dump: %.riscv
	$(OBJDUMP) -D $< > $@

$(bmarks_riscv_out): %.riscv.out: %.riscv
	$(RISCV_SIM) $< 2> $@

$(bmarks_riscv_log): %.riscv.log: %.riscv
	$(RISCV_SIM) --log-commits $< 2> $@

.PHONY: dump
dump: $(bmarks_riscv_dump)

.PHONY: run
run: $(bmarks_riscv_out)

.PHONY: clean
clean:
	rm -f -- $(bmarks_riscv_bin) $(bmarks_riscv_out) $(bmarks_riscv_log)

#------------------------------------------------------------
# Submit

define zip_template
.PHONY: $(1)
$(1): $(addprefix $(srcdir)/,$(2))
	rm -f -- $(1).zip
	zip -j $(1).zip $$^
endef

$(eval $(call zip_template,zip-vvadd,mt-vvadd-opt/mt-vvadd_opt.c))
$(eval $(call zip_template,zip-matmul,mt-matmul-opt/mt-matmul_opt.c))


.SUFFIXES:
