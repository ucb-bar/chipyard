#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define N 64  // Size of the matrices (NxN)

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

    // Initialize matrices A and B with some values (for example, random values)
    srand(time(NULL)); // Seed for random number generation
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            A[i][j] = (float)(rand() % 100);
            B[i][j] = (float)(rand() % 100);
        }
    }

    // Start timing the matrix multiplication
    clock_t start = clock();

    // Perform matrix multiplication
    matrix_multiply(A, B, C);

    // Stop timing the matrix multiplication
    clock_t end = clock();

    // Calculate the time taken in seconds
    double cpu_time_used = ((double) (end - start)) / CLOCKS_PER_SEC;

    // Output the result of the benchmark
    printf("CPU Matrix Multiplication took %f seconds\n", cpu_time_used);

    return 0;
}
