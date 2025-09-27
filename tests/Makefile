#################################
# RISCV Toolchain
#################################

TARGET = /home/willis/chipyard/.conda-env/riscv-tools/bin/riscv64-unknown-elf

GCC = $(TARGET)-gcc
CXX = $(TARGET)-g++
CP = $(TARGET)-objcopy
OBJDUMP = $(TARGET)-objdump
DG = $(TARGET)-gdb
SIZE = $(TARGET)-size

COMMON_SRCS := cordic.c
COMMON_OBJS := $(COMMON_SRCS:.c=.o)
$(COMMON_OBJS): %.o : %.c mmio.h cordic.h
	$(GCC) $(CFLAGS) -c $< -o $@
#################################
# Flags
#################################

# SoC Settings
ARCH = rv64imafdc
ABI = lp64d
ARCHFLAGS = -march=$(ARCH) -mabi=$(ABI)

CFLAGS  = -std=gnu99 -O2 -fno-common -fno-builtin-printf -Wall 
CFLAGS += $(ARCHFLAGS)
LDFLAGS = -static
LDLIBS = -lm -Wl,-u,_printf_float
include libgloss.mk

PROGRAMS = pwm blkdev accum charcount nic-loopback big-blkdev pingd \
           streaming-passthrough streaming-fir nvdla spiflashread spiflashwrite fft gcd \
           hello mt-hello symmetric test SVD_2x2 fft_soft inv_gauss 


.DEFAULT_GOAL := default


#################################
# Build
#################################

spiflash.img: spiflash.py
	python3 $<

%.o: %.S
	$(GCC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

%.o: %.c mmio.h spiflash.h cordic.h
	$(GCC) $(CFLAGS) -c $< -o $@

%.riscv: %.o $(libgloss) $(COMMON_OBJS)
	$(GCC) $(LDFLAGS) $< $(COMMON_OBJS) -o $@ $(LDLIBS)


%.dump: %.riscv
	$(OBJDUMP) -D $< > $@


#################################
# Recipes
#################################

.PHONY: clean
clean:
	rm -f *.riscv *.o *.dump
	$(if $(libgloss),rm -rf $(libgloss_builddir)/)

.PHONY: default
default: $(addsuffix .riscv, $(PROGRAMS)) spiflash.img

.PHONY: dumps
dumps: $(addsuffix .dump, $(PROGRAMS))