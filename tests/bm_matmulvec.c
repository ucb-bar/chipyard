#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

// Matrix size (NxN)
#define N 32

// Function to read RISC-V cycles
static inline uint64_t read_cycles() {
    uint64_t cycles;
    asm volatile ("rdcycle %0" : "=r" (cycles));
    return cycles;
}

// Function to multiply matrix A and vector v, storing the result in vector b
void matrix_vector_multiply(volatile float A[N][N], volatile float v[N], volatile float b[N]) {
    for (int i = 0; i < N; i++) {
        b[i] = 0;
        for (int j = 0; j < N; j++) {
            b[i] += A[i][j] * v[j];
        }
    }
}

int main() {
    // Declare and allocate memory for matrix A, vector v, and result vector b as volatile
    volatile float A[N][N];
    volatile float v[N];
    volatile float b[N];

    // Initialize matrix A and vector v with random values
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            A[i][j] = (float)(rand() % 100);
        }
        v[i] = (float)(rand() % 100);
    }

    // Start timing the matrix-vector multiplication
    uint64_t start_cycles = read_cycles();
    matrix_vector_multiply(A, v, b);
    uint64_t end_cycles = read_cycles();

    // Output the number of cycles taken
    printf("Matrix-Vector Multiplication completed.\n");
    printf("Cycles taken: %lu\n", end_cycles - start_cycles);

    // Optionally, print the resulting vector b
    printf("Result vector:\n");
    for (int i = 0; i < N; i++) {
        printf("%f ", b[i]);
    }
    printf("\n");

    return 0;
}
