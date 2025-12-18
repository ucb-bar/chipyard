#include <stdio.h>
#include <riscv-pk/encoding.h>
#include "marchid.h"
#include <stdint.h>

/* A weak definition overwritten by another declaration of the function */
__attribute__ ((weak)) int sbst(void) {

  return 0;
}

int main(void) {
    uint64_t marchid = read_csr(marchid);
    const char* march = get_march(marchid);
    printf("Hello world from core 0, a %s\n", march);

    volatile int signature = sbst();

    printf("The final signature is %d\n", signature);
    return 0;
}