#include "spectre.h"

struct spectre_result spectre_result;
struct spectre_secret spectre_secret = {
  /* Training indices all hit line 0; the probe skips it. */
  {0},
  "!\"#ThisIsTheBabyBoomerTest"
};

extern volatile uint64_t tohost;

__asm__(".globl begin_signature\n"
        "begin_signature = spectre_result\n"
        ".globl end_signature\n"
        "end_signature = spectre_result + 128\n");

static volatile uint8_t flush_sink;

static void flush_set(uint64_t addr)
{
  volatile uint8_t *p = (volatile uint8_t *)FLUSH_BASE +
                        ((addr >> 6) & (L1_SETS - 1)) * L1_BLOCK;
  uint8_t acc = 0;
  for (uint64_t w = 0; w < L1_WAYS; w++)
    acc ^= p[w * 4096];
  flush_sink = acc;
}

void spectre_flush(void)
{
  volatile uint8_t *p = (volatile uint8_t *)FLUSH_BASE;
  uint8_t acc = 0;
  for (uint64_t s = 0; s < L1_SETS; s++)
    for (uint64_t w = 0; w < FLUSH_WAYS; w++)
      acc ^= p[s * L1_BLOCK + w * 4096];
  flush_sink = acc;
}

void spectre_top_two(const uint8_t *hits, uint8_t *idx, uint64_t *cnt)
{
  cnt[0] = 0;
  cnt[1] = 0;
  idx[0] = 0;
  idx[1] = 0;
  for (uint64_t i = 0; i < 256; i++) {
    if (hits[i] > cnt[0]) {
      cnt[1] = cnt[0];
      idx[1] = idx[0];
      cnt[0] = hits[i];
      idx[0] = (uint8_t)i;
    } else if (hits[i] > cnt[1]) {
      cnt[1] = hits[i];
      idx[1] = (uint8_t)i;
    }
  }
}

void spectre_finish(void)
{
  uint32_t mask = 0;

  for (int i = 0; i < SECRET_SZ; i++)
    if (spectre_result.recovered[i] == (uint8_t)spectre_secret.secret[i])
      mask |= 1u << i;

  spectre_result.magic = SPECTRE_MAGIC;
  spectre_result.mask = mask;

  /* fesvr reads memory directly, so dirty lines must be evicted first.
   * Random replacement needs several evictions per set, not one. */
  spectre_flush();

  /* Always exit 0: the harness aborts on a nonzero exit and never prints the
   * simulation-cycle count. The verdict is `mask`; run.sh exits 1 on a leak. */
  for (;;) {
    tohost = 1;
    flush_set((uint64_t)&tohost);
  }
}
