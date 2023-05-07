#include <riscv-pk/encoding.h>
#include <stdio.h>
#include "marchid.h"

// EDIT THIS
static size_t n_cores = 4;

static void __attribute__((noinline)) barrier()
{
  static volatile int sense;
  static volatile int count;
  static __thread int threadsense;

  __sync_synchronize();

  threadsense = !threadsense;
  if (__sync_fetch_and_add(&count, 1) == n_cores-1)
  {
    count = 0;
    sense = threadsense;
  }
  else while(sense != threadsense)
    ;

  __sync_synchronize();
}

void __main(void) {
  size_t mhartid = read_csr(mhartid);

  if (mhartid >= n_cores) while (1);

  const char* march = get_march(read_csr(marchid));
  for (size_t i = 0; i < n_cores; i++) {
    if (mhartid == i) {
      printf("Hello world from core %lu, a %s\n", mhartid, march);
    }
    barrier();
  }

  // Spin if not core 0
  if (mhartid > 0) while (1);
}

int main(void) {
  __main();
  return 0;
}
