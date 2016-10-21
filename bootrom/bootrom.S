.text
.global _start
_start:
  // This boot ROM doesn't know about any boot devices, so it just spins,
  // waiting for the serial interface to load the program and interrupt it
  j setup_wfi_loop // reset vector
  .word 0 // reserved
  .word 0 // reserved
  .word 0 // pointer to config string
default_trap_vec:
  j boot_trap // default trap vector
  .word 0
  .word 0
  .word 0

setup_wfi_loop:
  la a0, default_trap_vec
  csrw mtvec, a0
  li a0, 8 // MIE or MSIP bit
  csrw mie, a0 // set only MSIP in mie CSR
  csrw mideleg, zero // no delegation
  csrs mstatus, a0 // set MIE in mstatus CSR
wfi_loop:
  wfi
  j wfi_loop

boot_trap:
  csrr a0, mhartid
  sll a0, a0, 2 // offset for hart msip
  li a1, 0x2000000 // base address of clint
  add a0, a0, a1
  sw zero, 0(a0) // clear the interrupt
  li a0, 0x80000000 // program reset vector
  csrw mepc, a0 // return from interrupt to start of user program
  mret
