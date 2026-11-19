#ifndef SPECTRE_H
#define SPECTRE_H

#include <stdint.h>

#define SECRET_SZ 26
#define L1_SETS 64
#define L1_WAYS 4
/* random replacement: 16 evictions per set leave ~1% of the old lines */
#define FLUSH_WAYS 16
#define L1_BLOCK 64

/* Fixed DRAM page used to evict cache lines without a dummyMem in .bss.
 * set s lives at FLUSH_BASE + s*64 + w*4096, w = 0..FLUSH_WAYS-1. */
#define FLUSH_BASE 0x80400000UL
/* Probe arrays live in raw DRAM: no .bss zeroing (large BSS hangs SimDRAM). */
#define ARRAY2_BASE 0x80600000UL

#define SPECTRE_MAGIC 0x53504543u

#ifndef CACHE_HIT_THRESHOLD
#define CACHE_HIT_THRESHOLD 52
#endif

/* 128 bytes, dumped by fesvr through +signature=FILE. */
struct spectre_result {
  uint32_t magic;
  uint32_t mask; /* bit i set: recovered[i] == secret[i] */
  uint64_t cycles; /* rdcycle() delta of the whole attack */
  uint8_t recovered[SECRET_SZ];
  uint8_t alt[SECRET_SZ];
  uint8_t count0[SECRET_SZ];
  uint8_t count1[SECRET_SZ];
  uint8_t attempted; /* secret bytes attacked (ATTACK_BYTES) */
  uint8_t rounds;    /* probe rounds per byte (ROUNDS) */
  uint8_t pad[6];
};

/* array1[160] is immediately followed by the secret, so the OOB index is 160. */
struct spectre_secret {
  uint8_t array1[160];
  char secret[SECRET_SZ + 1];
};

extern struct spectre_result spectre_result;
extern struct spectre_secret spectre_secret;

static inline uint64_t spectre_rdcycle(void)
{
  uint64_t x;
  __asm__ volatile("rdcycle %0" : "=r"(x));
  return x;
}

/* Evict the whole L1D. */
void spectre_flush(void);
/* Top two hit counts. */
void spectre_top_two(const uint8_t *hits, uint8_t *idx, uint64_t *cnt);
/* Record magic/mask, flush the result, then exit with tohost. Never returns. */
void spectre_finish(void) __attribute__((noreturn));

#endif
