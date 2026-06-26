  .section ".text.init"
  .globl _start
_start:
  # Initialize registers
  li x1, 0
  li x2, 0
  li x3, 0
  li x4, 0
  li x5, 0
  li x6, 0
  li x7, 0
  li x8, 0
  li x9, 0
  li x10, 0
  li x11, 0
  li x12, 0
  li x13, 0
  li x14, 0
  li x15, 0
  li x16, 0
  li x17, 0
  li x18, 0
  li x19, 0
  li x20, 0
  li x21, 0
  li x22, 0
  li x23, 0
  li x24, 0
  li x25, 0
  li x26, 0
  li x27, 0
  li x28, 0
  li x29, 0
  li x30, 0
  li x31, 0

  # Enable FPU
  li t0, 0x00006000  # MSTATUS_FS
  csrs mstatus, t0

  # Set up trap handler
  la t0, _trap_handler
  csrw mtvec, t0

  # Set up global pointer
.option push
.option norelax
  la gp, __global_pointer$
.option pop

  # Set up stack pointer (128KB stack)
  la sp, _end
  li t0, 0x20000
  add sp, sp, t0

  # Initialize BSS
  la t0, __bss_start
  la t1, __bss_end
1:
  bge t0, t1, 2f
  sd zero, 0(t0)
  addi t0, t0, 8
  j 1b
2:

  # Call main
  jal ra, main

  # Write exit code to tohost: (ret << 1) | 1
  slli a0, a0, 1
  ori a0, a0, 1
  la t0, tohost
  sd a0, 0(t0)
1:
  j 1b

  .align 2
_trap_handler:
  # On trap, write 1337 to tohost and hang
  li a0, 2675  # (1337 << 1) | 1
  la t0, tohost
  sd a0, 0(t0)
1:
  j 1b

.section ".tohost","aw",@progbits
.align 6
.globl tohost
tohost: .dword 0
.align 6
.globl fromhost
fromhost: .dword 0
