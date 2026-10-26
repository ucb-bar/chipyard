# Build the bare-metal Ethernet applications for RocketNexysVideoVerilogEthConfig.
#
#   make            # build all FPGA-side Ethernet binaries
#   make dump       # disassembly
#   make clean

PREFIX  ?= riscv64-unknown-elf-
CC      := $(PREFIX)gcc
OBJDUMP := $(PREFIX)objdump

ARCH    := rv64imafd_zifencei
ABI     := lp64d
CMODEL  := medany

CFLAGS   = -std=c11 -Os -Wall -Wextra -fno-common -fno-builtin-printf \
           -ffunction-sections -fdata-sections \
           -march=$(ARCH) -mabi=$(ABI) -mcmodel=$(CMODEL) -specs=htif_nano.specs \
           -I../.. $(EXTRA_CFLAGS)
LDFLAGS := -static -T htif.ld -Wl,--gc-sections -Wl,--wrap=atexit

TARGETS := ethernet_fpga.riscv ethernet_sim_file_transfer.riscv \
           ethernet_udp_fpga.riscv ethernet_udp_sim_file_transfer.riscv \
           ethernet_sim_loopback.riscv ethernet_phy_loopback.riscv \
           ethernet_phy_loopback_1g.riscv
HDRS    := mmio_config.h eth.h eth_udp.h ethernet_file_echo.h \
           ethernet_file_transport.h ethernet_udp_file_echo.h mdio.h \
           rtl8211e.h protocol.h

all: $(TARGETS)

ethernet_fpga.riscv: ethernet_fpga.c ethernet_file_echo.c protocol.c $(HDRS) htif.ld
ethernet_sim_file_transfer.riscv: ethernet_sim_file_transfer.c ethernet_file_echo.c \
                                  protocol.c $(HDRS) htif.ld
ethernet_udp_fpga.riscv: ethernet_udp_fpga.c eth_udp.c \
                         ethernet_udp_file_echo.c ethernet_file_echo.c \
                         protocol.c $(HDRS) htif.ld
ethernet_udp_sim_file_transfer.riscv: ethernet_udp_sim_file_transfer.c eth_udp.c \
                                      ethernet_udp_file_echo.c \
                                      ethernet_file_echo.c protocol.c \
                                      $(HDRS) htif.ld
ethernet_sim_loopback.riscv: ethernet_sim_loopback.c $(HDRS) htif.ld
ethernet_phy_loopback.riscv: ethernet_phy_loopback.c $(HDRS) htif.ld
ethernet_phy_loopback_1g.riscv: EXTRA_CFLAGS += -DPHY_LOOPBACK_SPEED_MBPS=1000
ethernet_phy_loopback_1g.riscv: ethernet_phy_loopback.c $(HDRS) htif.ld

# Match Chipyard's GCC 16 nano-Newlib exit workaround.
$(TARGETS): ../../tests/atexit-workaround.c

$(TARGETS):
	$(CC) $(CFLAGS) $(filter %.c,$^) $(LDFLAGS) -o $@

dump: $(TARGETS)
	$(foreach t,$(TARGETS),$(OBJDUMP) -D $(t) > $(t:.riscv=.dump);)

clean:
	rm -f $(TARGETS) $(TARGETS:.riscv=.dump)

.PHONY: all dump clean
