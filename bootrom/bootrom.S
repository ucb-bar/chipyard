#define DRAM_BASE 0x80000000

.section .text.start, "ax", @progbits
.globl _start
_start:
  li a1, 0x2000000 // base address of clint
  csrr a0, mhartid
  bnez a0, boot_core

  addi a2, a1, 4
  li a3, 1
interrupt_loop:
  sw a3, 0(a2)
  addi a2, a2, 4
  lw a3, -4(a2)
  bnez a3, interrupt_loop
  j boot_core_hart0

.section .text.hang, "ax", @progbits
.globl _hang
_hang:
  // This boot ROM doesn't know about any boot devices, so it just spins,
  // waiting for the serial interface to load the program and interrupt it
  la a0, _start
  csrw mtvec, a0
  li a0, 8 // MIE or MSIP bit
  csrw mie, a0 // set only MSIP in mie CSR
  csrw mideleg, zero // no delegation
  csrs mstatus, a0 // set MIE in mstatus CSR
wfi_loop:
  wfi
  j wfi_loop

boot_core:
  lw t0, 0(a1) // load hart 0 msip
  bnez t0, boot_core // block until hart 0 clears own msip
  sll a0, a0, 2 // offset for hart msip
  add a1, a0, a1
boot_core_hart0:
  sw zero, 0(a1) // clear the interrupt
  li a0, DRAM_BASE // program reset vector
  csrw mepc, a0 // return from interrupt to start of user program
  csrr a0, mhartid // hartid for next level bootloader
  la a1, _dtb // dtb address for next level bootloader
  li a2, 0x80 // set mstatus MPIE to 0
  csrc mstatus, a2
  mret

_dtb:
