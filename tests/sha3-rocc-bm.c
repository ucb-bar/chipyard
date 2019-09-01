#include <stdio.h>
#include "sha3.h"
#include "encoding.h"

#ifdef VERBOSE
#define printf_info(fmt, ...) printf((fmt), ##__VA_ARGS__)
#else
#define printf_info(fmt, ...) do { } while (0)
#endif

int main(void)
{
    // BASIC TEST: 150 zero bytes

    // Setup test data
    unsigned char input[150] = { 0 };
    unsigned char output[SHA3_256_DIGEST_SIZE];

    __asm__ __volatile__ ("fence");
    unsigned long start = rdcycle();

    // Setup accelerator with input and output pointers
    //                      opcode rd  rs1          rs2          funct
    __asm__ __volatile__ ("custom2 x0, %[msg_addr], %[hash_addr], 0"
        :: [msg_addr] "r" (&input), [hash_addr] "r" (&output));

    // Set length and compute hash
    //                      opcode rd  rs1        rs2 funct
    __asm__ __volatile__ ("custom2 x0, %[length], x0, 1"
        :: [length] "r" (sizeof(input)));

    __asm__ __volatile__ ("fence");
    unsigned long end = rdcycle();

    printf("SHA3-256: %lu cycles\n", end - start);

    // Check result
    static const unsigned char result[SHA3_256_DIGEST_SIZE] = {
        0xdd, 0xcc, 0x9d, 0xd9, 0x43, 0xd3, 0x56, 0x1f,
        0x36, 0xa8, 0x2c, 0xf5, 0x61, 0xc2, 0xc1, 0x1a,
        0xea, 0x2a, 0x87, 0xa6, 0x42, 0x86, 0x27, 0xae,
        0xb8, 0x3d, 0x03, 0x95, 0x89, 0x2a, 0x39, 0xee,
    };

    int i;
    for (i = 0; i < SHA3_256_DIGEST_SIZE; i++){
        printf_info("[%2d] 0x%02x\n", i, output[i]);
        if (result[i] != output[i]) {
            printf_info("mismatch: expected 0x%02x\n", result[i]);
            return 1;
        }
    }

    printf_info("SUCCESS!\n");
    return 0;
}
