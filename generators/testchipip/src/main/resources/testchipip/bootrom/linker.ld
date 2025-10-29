SECTIONS
{
    ROM_BASE = 0x10000; /* ... but actually position independent */

    . = ROM_BASE;
    .text.hang : { *(.text.hang) }
    . = ROM_BASE + 0x40;
    .text.start : { *(.text.start) }
    . = ROM_BASE + 0x80;
    .text.hang80 : { *(.text.hang80) }
    . = ROM_BASE + 0x100;
    .text.hang100 : { *(.text.hang100) }
}
