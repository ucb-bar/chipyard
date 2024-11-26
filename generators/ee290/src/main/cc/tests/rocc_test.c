#include <stdio.h>
#include <riscv-pk/encoding.h>

#include <stdio.h>
#include <stdint.h>


#include "marchid.h"
#include "rocc.h"

// Utility function to generate a random 8x8 bit matrix
uint64_t generate_random_bitmat() {
    uint64_t mat = 0;
    for (int i = 0; i < 64; ++i) {
        mat |= ((uint64_t)(rand() & 1) << i);
    }
    return mat;
}

// Utility function to print an 8x8 bit matrix
void print_bitmat(uint64_t mat) {
    for (int i = 0; i < 64; i++) {
        printf("%x", (mat >> (63 - i)) & 1);
        if ((i + 1) % 8 == 0) printf("\n");
        // else printf("");
    }
    printf("\n");
}

// Function to perform bit matrix multiplication using the RoCC accelerator
uint64_t rocc_bmm(uint64_t a, uint64_t b) {
  uint64_t result;
  ROCC_INSTRUCTION_DSS(0, result, a, b, 0x0); 
  return result;
}

uint64_t cpu_bmm(uint64_t a, uint64_t b) {
    uint64_t result = 0;
    for (int i = 0; i < 8; ++i) {
        for (int j = 0; j < 8; ++j) {
            uint8_t bit_result = 0;
            for (int k = 0; k < 8; ++k) {
                bit_result ^= ((a >> (63 - (i*8 + k))) & 1) & ((b >> (63 - (k*8 + j))) & 1);
            }
            result |= ((uint64_t)bit_result << (63 - (i*8 + j)));
        }
    }
    return result;
}

int main(void) {
    uint64_t marchid = read_csr(marchid);
    const char* march = get_march(marchid);
    printf("RoCC BMM test on core %s\n\n", march);
    
    srand(0);

    // Example test matrices
    uint64_t mat1 = 0xF0F0F0F00F0F0F0F; // Example matrix
    uint64_t mat2 = 0xFF00FF0000FF00FF; // Example matrix
    uint64_t identity = 0x8040201008040201; // Identity matrix
    uint64_t rand_mat1 = generate_random_bitmat();
    uint64_t rand_mat2 = generate_random_bitmat();

    // Perform BMM operation using RoCC
    uint64_t result = rocc_bmm(mat1, mat2);
    // Perform BMM operation using CPU-based golden model
    uint64_t expected = cpu_bmm(mat1, mat2);


    // Verify RoCC result against CPU-based golden model
    if (result != expected) {
        printf("Test failed. RoCC result does not match CPU-based golden model.\n");
        // Print matrices and result
        printf("Matrix 1:\n");
        print_bitmat(mat1);
        printf("Matrix 2:\n");
        print_bitmat(mat2);
        printf("BMM Result (RoCC):\n");
        print_bitmat(result);
        printf("Expected BMM Result (CPU):\n");
        print_bitmat(expected);
    } else {
        printf("Test passed. RoCC result matches CPU-based golden model.\n");
    }

    // Test case with an identity matrix
    printf("\nTesting multiplication by identity matrix:\n");
    uint64_t identityResult = rocc_bmm(mat1, identity);

    // Verify multiplication by identity matrix
    if (identityResult != mat1) {
        printf("Identity matrix test failed.\n");
        printf("Matrix:\n");
        print_bitmat(mat1);
        printf("Identity Matrix:\n");
        print_bitmat(identity);
        printf("Result of multiplication by identity matrix:\n");
        print_bitmat(identityResult);
    } else {
        printf("Identity matrix test passed. RoCC result matches CPU-based golden model\n");
    }


    // Perform BMM operation using RoCC
    uint64_t rand_result = rocc_bmm(rand_mat1, rand_mat2);

    // Perform BMM operation using CPU-based golden model
    uint64_t rand_expected = cpu_bmm(rand_mat1, rand_mat2);

    // Verify RoCC result against CPU-based golden model for random matrices
    if (rand_result != rand_expected) {
        printf("Random test failed. RoCC result does not match CPU-based golden model.\n");
        // Print the random matrices and result
        printf("Random Matrix 1:\n");
        print_bitmat(rand_mat1);
        printf("Random Matrix 2:\n");
        print_bitmat(rand_mat2);
        printf("BMM Result (RoCC) for Random Matrices:\n");
        print_bitmat(rand_result);
        printf("Expected BMM Result (CPU) for Random Matrices:\n");
        print_bitmat(rand_expected);
    } else {
        printf("Random test passed. RoCC result matches CPU-based golden model.\n");
    }


    return 0;
}

