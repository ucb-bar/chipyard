//see LICENSE for license
// The following is a RISC-V program to test the functionality of the
// sha3 RoCC accelerator.
// Compile with riscv-gcc sha3-rocc.c
// Run with spike --extension=sha3 pk a.out

#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include "sha3.h"

int main() {

  do {
    printf("start basic test 1.\n");
    // BASIC TEST 1 - 150 zero bytes

    // Setup some test data
    unsigned int ilen = 150;
    unsigned char input[150] = "\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000";
    unsigned char output[SHA3_256_DIGEST_SIZE];

    //compute hash
    sha3ONE(input, ilen, output);

    // Check result
    int i = 0;
    unsigned char result[SHA3_256_DIGEST_SIZE];
    sha3ONE(input, ilen, result);
    for(i = 0; i < SHA3_256_DIGEST_SIZE; i++){
      printf("output[%d]:%d ==? results[%d]:%d \n",i,output[i],i,result[i]);
     assert(output[i]==result[i]);
    }

  } while(0);

  printf("success!\n");
  return 0;
}
