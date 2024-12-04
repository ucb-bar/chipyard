#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

// Matrix size (NxN)
#define N 68

// Function to read RISC-V cycles
static inline uint64_t read_cycles() {
    uint64_t cycles;
    asm volatile ("rdcycle %0" : "=r" (cycles));
    return cycles;
}

// Function to multiply two matrices A and B and store the result in matrix C
void matrix_multiply(float A[N][N], float B[N][N], float C[N][N]) {
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            C[i][j] = 0;
            for (int k = 0; k < N; k++) {
                C[i][j] += A[i][k] * B[k][j];
            }
        }
    }
}

int main() {
    // Declare and allocate memory for matrices A, B, and C
    float A[N][N];
    float B[N][N];
    float C[N][N];

    // Initialize matrices A and B with random values
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            A[i][j] = (float)(rand() % 100);
            B[i][j] = (float)(rand() % 100);
        }
    }

    // Start timing the matrix multiplication
    uint64_t start_cycles = read_cycles();
    matrix_multiply(A, B, C);
    uint64_t end_cycles = read_cycles();

    // Output the number of cycles taken
    printf("Matrix Multiplication completed.\n");
    printf("Cycles taken: %lu\n", end_cycles - start_cycles);

    return 0;
}
