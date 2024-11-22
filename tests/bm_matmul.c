#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define N 64  // Size of the matrices (NxN)

// Function to multiply two matrices A and B and store the result in matrix C
void matrix_multiply(float local_A[N][N], float local_B[N][N], float local_C[N][N]) {
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            local_C[i][j] = 0;
            for (int k = 0; k < N; k++) {
                local_C[i][j] += local_A[i][k] * local_B[k][j];
            }
        }
    }
}

int main() {
    // Declare matrices as volatile to simulate hardware behavior
    volatile float A[N][N];
    volatile float B[N][N];
    volatile float C[N][N];

    // Declare local matrices for computation
    float local_A[N][N];
    float local_B[N][N];
    float local_C[N][N];

    // Initialize volatile matrices A and B with random values
    srand(time(NULL)); // Seed for random number generation
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            A[i][j] = (float)(rand() % 100);
            B[i][j] = (float)(rand() % 100);
        }
    }

    // Copy data from volatile matrices to local matrices
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            local_A[i][j] = A[i][j];
            local_B[i][j] = B[i][j];
        }
    }

    // Start timing the matrix multiplication
    clock_t start = clock();

    // Perform matrix multiplication
    matrix_multiply(local_A, local_B, local_C);

    // Stop timing the matrix multiplication
    clock_t end = clock();

    // Copy the result to the volatile output matrix
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            C[i][j] = local_C[i][j];
        }
    }

    // Save the result to an external file
    FILE *output_file = fopen("matrix_output.txt", "w");
    if (output_file == NULL) {
        perror("Error opening file");
        return EXIT_FAILURE;
    }
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            fprintf(output_file, "%f ", C[i][j]);
        }
        fprintf(output_file, "\n");
    }
    fclose(output_file);

    // Calculate the time taken in seconds
    double cpu_time_used = ((double)(end - start)) / CLOCKS_PER_SEC;

    // Output the result of the benchmark
    printf("CPU Matrix Multiplication took %f seconds\n", cpu_time_used);

    return 0;
}
