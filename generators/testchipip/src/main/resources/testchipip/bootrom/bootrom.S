#define BOOTADDR_REG 0x1000

// boot all cores (only hart 0) and jump to main program execution
.section .text.start, "ax", @progbits
.globl _start
_start:
  li a1, 0x2000000 // base address of clint
  csrr a0, mhartid
  bnez a0, boot_core
  addi a2, a1, 4
  li a3, 1
interrupt_loop: // trigger MSIP interrupts for other harts so that they also boot
  sw a3, 0(a2)
  addi a2, a2, 4
  lw a3, -4(a2)
  bnez a3, interrupt_loop
  j boot_core_hart0

// this boot ROM doesn't know about any boot devices, so it just spins,
// waiting for the serial interface to load the program and interrupt it
.section .text.hang, "ax", @progbits
.globl _hang
_hang: // reset vector
  la a0, _start          // on MSIP interrupt, go to _start
  csrw mtvec, a0
  csrr t0, misa          // get MISA mask
  srai t0, t0, 'S' - 'A' // get 'S' supervisor bit
  andi t0, t0, 0x1
  beqz t0, 1f            // only zero mideleg if supported
  csrw mideleg, zero     // no delegation
  li a0, 0x222           // clear spurious seip/stip/ssip left over after reset
  csrc mip, a0
1:
  li a0, 8               // MIE or MSIP bit
  csrw mie, a0           // set only MSIP in MIE CSR
  csrs mstatus, a0       // set MIE in mstatus CSR

wfi_loop: // wait for MSIP interrupt to start program execution
  wfi
  j wfi_loop

// jump to reset vector
.section .text.hang80, "ax", @progbits
.globl _hang80
_hang80:
  j _hang

// other harts must wait until MSIP of hart 0 is cleared to continue
boot_core:
  lw t0, 0(a1)       // load hart 0 MSIP
  bnez t0, boot_core // block until hart 0 clears own MSIP
  sll a0, a0, 2      // offset for hart MSIP
  add a1, a0, a1
boot_core_hart0:      // begin executing code at DRAM_BASE
  sw zero, 0(a1)      // clear the interrupt
  li a0, BOOTADDR_REG // program reset vector
#if __riscv_xlen == 32
  lw a0, 0(a0)        // get boot address from bootaddr_reg SCR
#else
  ld a0, 0(a0)        // get boot address from bootaddr_reg SCR
#endif
  csrw mepc, a0       // return from interrupt to start of user program
  csrr a0, mhartid    // hartid for next level bootloader
  la a1, _dtb         // dtb address for next level bootloader
  li a2, 0x80         // set mstatus MPIE to 0
  csrc mstatus, a2
  mret

.align 3
_dtb:
