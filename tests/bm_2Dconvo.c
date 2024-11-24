#include <stdio.h>
#include <stdlib.h>
#include <time.h>

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
    int inputRows = 256; // Input dimensions
    int inputCols = 256;
    int kernelRows = 3;  // Kernel dimensions
    int kernelCols = 3;

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

    // Measure time taken for convolution
    clock_t start = clock();
    convolve2D(input, kernel, output, inputRows, inputCols, kernelRows, kernelCols);
    clock_t end = clock();

    double time_spent = (double)(end - start) / CLOCKS_PER_SEC;

    printf("2D Convolution completed.\n");
    printf("Time taken: %f seconds\n", time_spent);

    // Free allocated memory
    free(input);
    free(kernel);
    free(output);

    return 0;
}
