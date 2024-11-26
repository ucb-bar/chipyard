#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <riscv-pk/encoding.h>
#include <stdint.h>

static inline uint64_t read_cycles() {
    uint64_t cycles;
    asm volatile ("rdcycle %0" : "=r" (cycles));
    return cycles;
}

// Function to perform 2D convolution
void convolve2D(float *input, float *kernel, float *output, 
                int inputRows, int inputCols, 
                int kernelRows, int kernelCols) {
    int padRows = kernelRows / 2;
    int padCols = kernelCols / 2;

    for (int i = 0; i < inputRows; i++) {
        for (int j = 0; j < inputCols; j++) {
            float sum = 0.0f;
            for (int ki = -padRows; ki <= padRows; ki++) {
                for (int kj = -padCols; kj <= padCols; kj++) {
                    int ni = i + ki;
                    int nj = j + kj;

                    if (ni >= 0 && ni < inputRows && nj >= 0 && nj < inputCols) {
                        sum += input[ni * inputCols + nj] * 
                               kernel[(ki + padRows) * kernelCols + (kj + padCols)];
                    }
                }
            }
            output[i * inputCols + j] = sum;
        }
    }
}

int main() {
    int inputRows = 32; // Input dimensions
    int inputCols = 32;
    int kernelRows = 2;  // Kernel dimensions
    int kernelCols = 2;

    // Allocate memory for input, kernel, and output
    float *input = (float *)malloc(inputRows * inputCols * sizeof(float));
    float *kernel = (float *)malloc(kernelRows * kernelCols * sizeof(float));
    float *output = (float *)malloc(inputRows * inputCols * sizeof(float));

    // Initialize input and kernel with random values
    srand(time(0));
    for (int i = 0; i < inputRows * inputCols; i++) {
        input[i] = rand() % 10 + 1;
    }
    for (int i = 0; i < kernelRows * kernelCols; i++) {
        kernel[i] = (rand() % 5 + 1) / 5.0f;
    }

    uint64_t start = read_cycles();
convolve2D(input, kernel, output, inputRows, inputCols, kernelRows, kernelCols);
uint64_t end = read_cycles();
printf("Cycles taken: %lu\n", end - start);

   

    printf("2D Convolution completed.\n");
    
    // Free allocated memory
    free(input);
    free(kernel);
    free(output);

    return 0;
}
