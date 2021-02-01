#ifndef SIFIVE_SMP
#define SIFIVE_SMP
#include "platform.h"

// The maximum number of HARTs this code supports
#ifndef MAX_HARTS
#define MAX_HARTS 32
#endif
#define CLINT_END_HART_IPI CLINT_CTRL_ADDR + (MAX_HARTS*4)
#define CLINT1_END_HART_IPI CLINT1_CTRL_ADDR + (MAX_HARTS*4)

// The hart that non-SMP tests should run on
#ifndef NONSMP_HART
#define NONSMP_HART 0
#endif

/* If your test cannot handle multiple-threads, use this: 
 *   smp_disable(reg1)
 */
#define smp_disable(reg1, reg2)			 \
  csrr reg1, mhartid				;\
  li   reg2, NONSMP_HART			;\
  beq  reg1, reg2, hart0_entry			;\
42:						;\
  wfi    					;\
  j 42b						;\
hart0_entry:

/* If your test needs to temporarily block multiple-threads, do this:
 *    smp_pause(reg1, reg2)
 *    ... single-threaded work ...
 *    smp_resume(reg1, reg2)
 *    ... multi-threaded work ...
 */

#define smp_pause(reg1, reg2)	 \
  li reg2, 0x8			;\
  csrw mie, reg2		;\
  li   reg1, NONSMP_HART	;\
  csrr reg2, mhartid		;\
  bne  reg1, reg2, 42f

#ifdef CLINT1_CTRL_ADDR
// If a second CLINT exists, then make sure we:
// 1) Trigger a software interrupt on all harts of both CLINTs.
// 2) Locate your own hart's software interrupt pending register and clear it.
// 3) Wait for all harts on both CLINTs to clear their software interrupt
//    pending register.
// WARNING: This code makes these assumptions, which are only true for Fadu as
// of now:
// 1) hart0 uses CLINT0 at offset 0
// 2) hart2 uses CLINT1 at offset 0
// 3) hart3 uses CLINT1 at offset 1
// 4) There are no other harts or CLINTs in the system.
#define smp_resume(reg1, reg2)	 \
  /* Trigger software interrupt on CLINT0 */ \
  li reg1, CLINT_CTRL_ADDR	;\
41:				;\
  li reg2, 1			;\
  sw reg2, 0(reg1)		;\
  addi reg1, reg1, 4		;\
  li reg2, CLINT_END_HART_IPI	;\
  blt reg1, reg2, 41b		;\
  /* Trigger software interrupt on CLINT1 */ \
  li reg1, CLINT1_CTRL_ADDR	;\
41:				;\
  li reg2, 1			;\
  sw reg2, 0(reg1)		;\
  addi reg1, reg1, 4		;\
  li reg2, CLINT1_END_HART_IPI	;\
  blt reg1, reg2, 41b		;\
  /* Wait to receive software interrupt */ \
42:				;\
  wfi    			;\
  csrr reg2, mip		;\
  andi reg2, reg2, 0x8		;\
  beqz reg2, 42b		;\
  /* Clear own software interrupt bit */ \
  csrr reg2, mhartid		;\
  bnez reg2, 41f; \
  /* hart0 case: Use CLINT0 */ \
  li reg1, CLINT_CTRL_ADDR	;\
  slli reg2, reg2, 2		;\
  add reg2, reg2, reg1		;\
  sw zero, 0(reg2)		;\
  j 42f; \
41: \
  /* hart 2, 3 case: Use CLINT1 and remap hart IDs to 0 and 1 */ \
  li reg1, CLINT1_CTRL_ADDR	;\
  addi reg2, reg2, -2; \
  slli reg2, reg2, 2		;\
  add reg2, reg2, reg1		;\
  sw zero, 0(reg2)		; \
42: \
  /* Wait for all software interrupt bits to be cleared on CLINT0 */ \
  li reg1, CLINT_CTRL_ADDR	;\
41:				;\
  lw reg2, 0(reg1)		;\
  bnez reg2, 41b		;\
  addi reg1, reg1, 4		;\
  li reg2, CLINT_END_HART_IPI	;\
  blt reg1, reg2, 41b; \
  /* Wait for all software interrupt bits to be cleared on CLINT1 */ \
  li reg1, CLINT1_CTRL_ADDR	;\
41:				;\
  lw reg2, 0(reg1)		;\
  bnez reg2, 41b		;\
  addi reg1, reg1, 4		;\
  li reg2, CLINT1_END_HART_IPI	;\
  blt reg1, reg2, 41b; \
  /* End smp_resume() */

#else

#define smp_resume(reg1, reg2)	 \
  li reg1, CLINT_CTRL_ADDR	;\
41:				;\
  li reg2, 1			;\
  sw reg2, 0(reg1)		;\
  addi reg1, reg1, 4		;\
  li reg2, CLINT_END_HART_IPI	;\
  blt reg1, reg2, 41b		;\
42:				;\
  wfi    			;\
  csrr reg2, mip		;\
  andi reg2, reg2, 0x8		;\
  beqz reg2, 42b		;\
  li reg1, CLINT_CTRL_ADDR	;\
  csrr reg2, mhartid		;\
  slli reg2, reg2, 2		;\
  add reg2, reg2, reg1		;\
  sw zero, 0(reg2)		;\
41:				;\
  lw reg2, 0(reg1)		;\
  bnez reg2, 41b		;\
  addi reg1, reg1, 4		;\
  li reg2, CLINT_END_HART_IPI	;\
  blt reg1, reg2, 41b

#endif  /* ifdef CLINT1_CTRL_ADDR */

#endif
