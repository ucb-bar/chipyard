#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>

#define MULE5N_FUNCT3 0x0
#define MULE5N_FUNCT7 0x0f

struct mule5n_test_case {
  int64_t a;
  int64_t b;
};

static inline uint64_t mule5n_hw(int64_t a, int64_t b)
{
  uint64_t rd;

  asm volatile (
      ".insn r CUSTOM_0, %3, %2, %0, %1, %4\n\t"
      : "=r" (rd)
      : "r" ((uint64_t)a), "i" (MULE5N_FUNCT7), "i" (MULE5N_FUNCT3), "r" ((uint64_t)b));

  return rd;
}

static inline uint64_t mul_hw(int64_t a, int64_t b)
{
  uint64_t rd;
  asm volatile (
      "mul %0, %1, %2\n\t"
      : "=r" (rd)
      : "r" ((uint64_t)a), "r" ((uint64_t)b));
  return rd;
}

static inline uint64_t mule5n_ref(int64_t a, int64_t b)
{
  return (uint64_t)((__int128)a * (__int128)b);
}

int main(void)
{
  static const struct mule5n_test_case tests[] = {
      {0, 0},
      {1, 1},
      {7, 9},
      {-7, 9},
      {-7, -9},
      {0x12345678ll, -0x1234ll},
      {0x7fffffffll, 0x7fffffffll},
      {0x123456789abcll, 0x10001ll},
      {INT64_MAX, 2},
      {INT64_MIN, 1},
      {INT64_MIN, -1},
      {INT64_MIN, INT64_MIN},
      {0x7fffffffffffffffll, 0x7ll},
      {-0x4000000000000000ll, 3},
  };

  for (unsigned i = 0; i < (sizeof(tests) / sizeof(tests[0])); i++) {
    const int64_t a = tests[i].a;
    const int64_t b = tests[i].b;
    const uint64_t mule5n = mule5n_hw(a, b);
    const uint64_t mul    = mul_hw(a, b);
    const uint64_t ref    = mule5n_ref(a, b);

    if (mule5n != ref || mul != ref || mule5n != mul) {
      printf("MULE5N mismatch at test %u\n", i);
      printf("  a      = 0x%016" PRIx64 " (%" PRId64 ")\n", (uint64_t)a, a);
      printf("  b      = 0x%016" PRIx64 " (%" PRId64 ")\n", (uint64_t)b, b);
      printf("  mule5n = 0x%016" PRIx64 "\n", mule5n);
      printf("  mul    = 0x%016" PRIx64 "\n", mul);
      printf("  ref    = 0x%016" PRIx64 "\n", ref);
      return (int)(i + 1);
    }
  }

  printf("MULE5N PASS (%u cases)\n",
         (unsigned)(sizeof(tests) / sizeof(tests[0])));
  return 0;
}
