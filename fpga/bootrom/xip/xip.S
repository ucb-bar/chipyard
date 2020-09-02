// See LICENSE for license details.
// Execute in place
// Jump directly to XIP_TARGET_ADDR

  .section .text.init
  .option norvc
  .globl _start
_start:
  csrr a0, mhartid
  la a1, dtb
  li t0, XIP_TARGET_ADDR
  jr t0

  .section .rodata
dtb:
  .incbin DEVICE_TREE
