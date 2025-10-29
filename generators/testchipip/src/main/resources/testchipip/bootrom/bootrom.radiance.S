// check hart id
// if hart id == 0:
//   memcopy operands
//   signal to other harts?
//   jump to 80000000
// else
//   jump to 80000000

// boot all cores (only hart 0) and jump to main program execution
.section .text.start, "ax", @progbits
.globl _start
_start:
  nop               // nop for 8 cycles
  nop               // to wait for irq deassert
  nop
  nop
  // HACK: this is trying to "rectify" the core pipeline after the interrupt.
  // The core doesn't support precise interrupts and the pipeline ends up
  // getting into a jumbled state.  Doing random jumps seem to fix the issue.
  beq x0, x0, b_target_0
b_target_0:
  bne x0, x0, b_target_1
b_target_1:
  beq x0, x0, b_target_2
b_target_2:
  bne x0, x0, b_target_3
b_target_3:
  beq x0, x0, b_target_4
b_target_4:
  bne x0, x0, b_target_5
b_target_5:
  beq x0, x0, b_target_6
b_target_6:
  bne x0, x0, b_target_7
b_target_7:
  nop
  j _hang100        // should not end up here, but still try to jump to normalcy

wake_other_cores:
  addi a2, a1, 4    // irq addr for hart 1
  li a3, 1
interrupt_loop:     // hart 0 triggers MSIP interrupts for other harts so that they also boot
  sw a3, 0(a2)
  addi a2, a2, 4
  lw a3, -4(a2)
  bnez a3, interrupt_loop
  j start_exec 

// this boot ROM doesn't know about any boot devices, so it just spins,
// waiting for the serial interface to load the program and interrupt it
.section .text.hang100, "ax", @progbits
.globl _hang100
_hang100: // reset vector
  li a1, 0x2000000   // base address of clint
  csrr a0, 0xcc5     // GCID
  slli a0, a0, 2
  add a1, a0, a1     // current hart msip address
wfi_loop:            // wait for MSIP interrupt to start program execution
  lw t0, 0(a1)
  bnez t0, loop_exit // interrupt received
  j wfi_loop
loop_exit:
  bnez a0, start_exec // if not hart 0, setup is done
  j wake_other_cores // otherwise first wake other cores 

boot_core:           // not used; waits for hart0 before booting 
  lw t0, 0(a1)       // load hart 0 MSIP
  bnez t0, boot_core // block until hart 0 clears own MSIP
  sll a0, a0, 2      // offset for this hart's MSIP
  add a1, a0, a1

start_exec:          // begin executing code at DRAM_BASE
  sw zero, 0(a1)     // clear the interrupt

  li a0, 0           // clear registers
  li a1, 0           // clear registers
  li a2, 0           // clear registers
  li a3, 0           // clear registers
  li t1, 0           // clear registers
  li s0, 0           // clear registers

  li t0, 0x80000000  // load DRAM start
  jr t0

.align 3
_dtb:
