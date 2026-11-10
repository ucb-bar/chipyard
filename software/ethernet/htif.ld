OUTPUT_ARCH ("riscv")
ENTRY (_start)

SECTIONS
{
    . = 0x80000000;

    /* Keep under a power of 2 to reserve space for TCB */
    PROVIDE (__stack_size_min = 24K);

    .text : {
        *(.text.init)
        *(.text.unlikely .text.unlikely.*)
        *(.text.startup .text.startup.*)
        *(.text .text.*)
        *(.gnu.linkonce.t.*)
    }
    PROVIDE (_etext = .);

    /* Static Thread Local Storage template */
    .tdata : {
        PROVIDE_HIDDEN (__tdata_start = .);
        *(.tdata .tdata.*)
        *(.gnu.linkonce.td.*)
        PROVIDE_HIDDEN (__tdata_end = .);
    }
    PROVIDE (__tdata_size = SIZEOF (.tdata));

    .tbss (NOLOAD) : {
        PROVIDE_HIDDEN (__tbss_start = .);
        PROVIDE_HIDDEN (__tbss_offset = ABSOLUTE (__tbss_start - __tdata_start));
        *(.tbss .tbss.*)
        *(.gnu.linkonce.tb.*)
        *(.tcommon)
        PROVIDE_HIDDEN (__tbss_end = .);
    }
    PROVIDE (__tbss_size = SIZEOF (.tbss));

    .rodata : {
        *(.rodata .rodata.*)
        *(.gnu.linkonce.r.*)
    }

    .preinit_array : {
        PROVIDE_HIDDEN (__preinit_array_start = .);
        KEEP (*(.preinit_array))
        PROVIDE_HIDDEN (__preinit_array_end = .);
    }

    .init_array : {
        PROVIDE_HIDDEN (__init_array_start = .);
        KEEP (*(SORT_BY_INIT_PRIORITY(.init_array.*) SORT_BY_INIT_PRIORITY(.ctors.*)))
        KEEP (*(.init_array EXCLUDE_FILE (*crtbegin.o *crtbegin?.o *crtend.o *crtend?.o ) .ctors))
        PROVIDE_HIDDEN (__init_array_end = .);
    }

    .fini_array : {
        PROVIDE_HIDDEN (__fini_array_start = .);
        KEEP (*(SORT_BY_INIT_PRIORITY(.fini_array.*) SORT_BY_INIT_PRIORITY(.dtors.*)))
        KEEP (*(.fini_array EXCLUDE_FILE (*crtbegin.o *crtbegin?.o *crtend.o *crtend?.o ) .dtors))
        PROVIDE_HIDDEN (__fini_array_end = .);
    }

    .ctors : {
        KEEP (*crtbegin.o(.ctors))
        KEEP (*crtbegin?.o(.ctors))
        KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o) .ctors))
        KEEP (*(SORT(.ctors.*)))
        KEEP (*(.ctors))
    }

    .dtors : {
        KEEP (*crtbegin.o(.dtors))
        KEEP (*crtbegin?.o(.dtors))
        KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o) .dtors))
        KEEP (*(SORT(.dtors.*)))
        KEEP (*(.dtors))
    }

    /* Host-Target Interface */
    .htif ALIGN(0x40) : {
        *(.htif)
    }

    .data : ALIGN (8) {
        *(.data)
        *(.gnu.linkonce.d.*)
    }

    .sdata : {
        PROVIDE (__global_pointer$ = . + 0x800);
        *(.sdata .sdata.*)
        *(.gnu.linkonce.s.*)
        *(.srodata.cst16)
        *(.srodata.cst8)
        *(.srodata.cst4)
        *(.srodata.cst2)
        *(.srodata .srodata.*)
    }

    PROVIDE (_edata = .);

    .bss : ALIGN (8) {
        PROVIDE_HIDDEN (__bss_start = .);
        *(.sbss .sbss.*)
        *(.gnu.linkonce.sb.*)
        *(.bss .bss.*)
        *(.gnu.linkonce.b.*)
        *(COMMON)
        PROVIDE_HIDDEN (__bss_end = .);
    }

    . = ALIGN (8);
    PROVIDE (_end = .);

    /* Reserve heap space */
    PROVIDE(__heap_size = 128K);
    . += __heap_size;
    . = ALIGN (4K);
    PROVIDE (__heap_end = .);

    /* Place Thread Control Block (TCB) at bottom of hart stack */
    PROVIDE (__tcb_size = __tbss_end - __tdata_start);
    PROVIDE (__stack_align = MAX (ALIGNOF (.tdata), 0x10));
    PROVIDE (__stack_start = ALIGN (__stack_align));
    PROVIDE (__stack_shift = LOG2CEIL( ALIGN (__stack_size_min + __tcb_size, __stack_align)));
    PROVIDE (__stack_size = 1 << __stack_shift);

    /* Default boot hart */
    PROVIDE (__boot_hart = 0);
}
