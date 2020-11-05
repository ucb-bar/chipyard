// See LICENSE for license details.
#ifndef _RISCV_TEST_DEFAULTS_H
#define _RISCV_TEST_DEFAULTS_H

#define TESTNUM x28
#define TESTBASE 0x4000

#define RVTEST_RV32U \
  .macro init; \
  .endm

#define RVTEST_RV64U \
  .macro init; \
  .endm

#define RVTEST_RV32UF \
  .macro init; \
  /* If FPU exists, initialize FCSR. */ \
  csrr t0, misa; \
  andi t0, t0, 1 << ('F' - 'A'); \
  beqz t0, 1f; \
  /* Enable FPU if it exists. */ \
  li t0, MSTATUS_FS; \
  csrs mstatus, t0; \
  fssr x0; \
1: ; \
  .endm

#define RVTEST_RV64UF \
  .macro init; \
  /* If FPU exists, initialize FCSR. */ \
  csrr t0, misa; \
  andi t0, t0, 1 << ('F' - 'A'); \
  beqz t0, 1f; \
  /* Enable FPU if it exists. */ \
  li t0, MSTATUS_FS; \
  csrs mstatus, t0; \
  fssr x0; \
1: ; \
  .endm

#define RVTEST_CODE_BEGIN \
  .section .text.init; \
  .globl _prog_start; \
_prog_start: \
  init;

#define RVTEST_CODE_END \
  unimp

#define RVTEST_PASS \
  fence; \
  li t0, TESTBASE; \
  li t1, 0x5555; \
  sw t1, 0(t0); \
1: \
  j 1b;

#define RVTEST_FAIL \
  li t0, TESTBASE; \
  li t1, 0x3333; \
  slli a0, a0, 16; \
  add a0, a0, t1; \
  sw a0, 0(t0); \
1: \
  j 1b;

#define EXTRA_DATA

#define RVTEST_DATA_BEGIN \
  EXTRA_DATA \
  .align 4; .global begin_signature; begin_signature:

#define RVTEST_DATA_END \
  _msg_init: .asciz "RUN\r\n"; \
  _msg_pass: .asciz "PASS"; \
  _msg_fail: .asciz "FAIL "; \
  _msg_end: .asciz "\r\n"; \
  .align 4; .global end_signature; end_signature:

#endif /* _RISCV_TEST_DEFAULTS_H */
