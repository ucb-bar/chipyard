# RISCV environment variable must be set

CC=$(RISCV)/bin/riscv64-unknown-elf-gcc
OBJCOPY=$(RISCV)/bin/riscv64-unknown-elf-objcopy
CFLAGS=-march=rv64imac -mcmodel=medany -O2 -std=gnu11 -Wall -nostartfiles 
CFLAGS+= -fno-common -g -DENTROPY=0 -mabi=lp64 -DNONSMP_HART=0 
CFLAGS+= -I $(BOOTROM_DIR)/include -I.
LFLAGS=-static -nostdlib -L $(BOOTROM_DIR)/linker -T bootrom.elf.lds

dts := $(BUILD_DIR)/$(CONFIG_PROJECT).$(CONFIG).dts
dtb := $(BUILD_DIR)/$(CONFIG_PROJECT).$(CONFIG).dtb
clk := $(BUILD_DIR)/$(CONFIG_PROJECT).$(CONFIG).tl_clock.h

## device tree
$(clk): $(dts)
	awk '/tlclk {/ && !f{f=1; next}; f && match($$0, /^.*clock-frequency.*<(.*)>.*/, arr) { print "#define TL_CLK " arr[1] "UL"}' $< > $@.tmp
	mv $@.tmp $@

$(dtb): $(dts)
	dtc -I dts -O dtb -o $@ $<

.PHONY: dtb
dtb: $(dtb)

## uart_boot
elf := $(BUILD_DIR)/bootrom.elf
$(elf): $(dtb) head.S crc16.c kprintf.c serial.c $(clk)
	$(CC) $(CFLAGS) -include $(clk) -DDEVICE_TREE='"$(dtb)"' $(LFLAGS) -o $@ head.S crc16.c kprintf.c serial.c

## sd_boot
# elf := $(BUILD_DIR)/bootrom.elf
# $(elf): $(dtb) head.S kprintf.c sd.c $(clk)
	# $(CC) $(CFLAGS) -include $(clk) -DDEVICE_TREE='"$(dtb)"' $(LFLAGS) -o $@ head.S sd.c kprintf.c

.PHONY: elf
elf: $(elf)

bin := $(BUILD_DIR)/bootrom.bin
$(bin): $(elf)
	$(OBJCOPY) -O binary $< $@

.PHONY: bin
bin: $(bin)

hex := $(BUILD_DIR)/bootrom.hex
$(hex): $(bin)
	od -t x4 -An -w4 -v $< > $@

.PHONY: hex
hex: $(hex)

# # Berkeley Boot Loader (BBL)
# elf := $(RISCV)/riscv64-unknown-elf/bin/bbl
# bin := $(BUILD_DIR)/bootrom.bin
# $(bin): $(elf)
# 	$(OBJCOPY) -O binary $< $@

# hex := $(BUILD_DIR)/bootrom.hex
# $(hex): $(bin)
# 	od -t x4 -An -w4 -v $< > $@

# Finally
romgen := $(BUILD_DIR)/rom.v
$(romgen): $(hex)
	$(rocketchip_dir)/scripts/vlsi_rom_gen $(ROMCONF) $< > $@

.PHONY: romgen
romgen: $(romgen)

.PHONY: clean
clean::
	rm -rf $(hex) $(elf)
