OUTPUT_ARCH("riscv")
ENTRY(_prog_start)

INCLUDE memory.lds

PHDRS
{
  text PT_LOAD;
  data PT_LOAD;
  bss PT_LOAD;
}

SECTIONS
{
  PROVIDE(_ram = ORIGIN(memory_mem));
  PROVIDE(_ram_end = _ram + LENGTH(memory_mem));

  .text ALIGN((ORIGIN(maskrom_mem) + 0x0), 8) : AT(ALIGN((ORIGIN(maskrom_mem) + 0x0), 8)) {
    PROVIDE(_ftext = .);
    *(.text.init)
    *(.text.unlikely .text.unlikely.*)
    *(.text .text.* .gnu.linkonce.t.*)
    PROVIDE(_etext = .);
    . += 0x40; /* to create a gap between .text and .data b/c ifetch can fetch ahead from .data */
  } >maskrom_mem  :text

  .eh_frame ALIGN((ADDR(.text) + SIZEOF(.text)), 8) : AT(ALIGN((LOADADDR(.text) + SIZEOF(.text)), 8)) {
    *(.eh_frame)
  } >maskrom_mem  :text

  .rodata ALIGN((ADDR(.eh_frame) + SIZEOF(.eh_frame)), 8) : AT(ALIGN((LOADADDR(.eh_frame) + SIZEOF(.eh_frame)), 8)) ALIGN_WITH_INPUT {
    *(.rodata .rodata.* .gnu.linkonce.r.*)
  } >maskrom_mem  :data

  .srodata ALIGN((ADDR(.rodata) + SIZEOF(.rodata)), 8) : AT(ALIGN((LOADADDR(.rodata) + SIZEOF(.rodata)), 8)) ALIGN_WITH_INPUT {
    *(.srodata.cst16)
    *(.srodata.cst8)
    *(.srodata.cst4)
    *(.srodata.cst2)
    *(.srodata.*)
  } >maskrom_mem  :data

  .data ALIGN((ADDR(.srodata) + SIZEOF(.srodata)), 8) : AT(ALIGN((LOADADDR(.srodata) + SIZEOF(.srodata)), 8)) ALIGN_WITH_INPUT {
    *(.data .data.* .gnu.linkonce.d.*)
    *(.tohost) /* TODO: Support sections that aren't explicitly listed in this linker script */
  } >maskrom_mem  :data

  .sdata ALIGN((ADDR(.data) + SIZEOF(.data)), 8) : AT(ALIGN((LOADADDR(.data) + SIZEOF(.data)), 8)) ALIGN_WITH_INPUT {
    *(.sdata .sdata.* .gnu.linkonce.s.*)
  } >maskrom_mem  :data

  PROVIDE(_data = ADDR(.rodata));
  PROVIDE(_data_lma = LOADADDR(.rodata));
  PROVIDE(_edata = .);

  .bss ALIGN((ORIGIN(memory_mem) + 0x0), 8) : AT(ALIGN((ORIGIN(memory_mem) + 0x0), 8)) ALIGN(8) {
    PROVIDE(_fbss = .);
    PROVIDE(__global_pointer$ = . + 0x7C0);
    *(.sbss .sbss.* .gnu.linkonce.sb.*)
    *(.bss .bss.* .gnu.linkonce.b.*)
    . = ALIGN(8);
    PROVIDE(_ebss = .);
  } >memory_mem  :bss

  PROVIDE(_end = .);

  /*
   * heap_stack_region_usable_end: (ORIGIN(memory_mem) + LENGTH(memory_mem))
   * heap_stack_min_size: 4096
   * heap_stack_max_size: 1048576
   */
  PROVIDE(_sp = ALIGN(MIN((ORIGIN(memory_mem) + LENGTH(memory_mem)), _ebss + 1048576) - 7, 8));
  PROVIDE(_heap_end = _sp - 2048);

  /* This section is a noop and is only used for the ASSERT */
  .stack : {
    ASSERT(_sp >= (_ebss + 4096), "Error: No room left for the heap and stack");
  }
}
