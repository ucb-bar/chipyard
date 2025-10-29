rom_img = rom.rv64.img rom.rv32.img
rom_dump = rom.rv64.dump rom.rv32.dump

GCC=riscv64-unknown-elf-gcc
CFLAGS_RV64=-mabi=lp64 -march=rv64ima
CFLAGS_RV32=-mabi=ilp32 -march=rv32ima
OBJCOPY=riscv64-unknown-elf-objcopy
OBJDUMP=riscv64-unknown-elf-objdump

default: img dump

img: $(rom_img)

dump: $(rom_dump)

%.img: %.elf
	$(OBJCOPY) -O binary --change-addresses=-0x20000 $< $@

%.rv32.elf: %.S linker.ld
	$(GCC) $(CFLAGS_RV32) -Tlinker.ld $< -nostdlib -static -o $@

%.rv64.elf: %.S linker.ld
	$(GCC) $(CFLAGS_RV64) -Tlinker.ld $< -nostdlib -static -o $@

%.dump: %.elf
	$(OBJDUMP) -d $< > $@

clean:
	rm -f *.elf *.dump *.img
