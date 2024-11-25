#include <sys/time.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

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
    int inputRows = 32; // Larger input dimensions
    int inputCols = 32;
    int kernelRows = 2;
    int kernelCols = 2;

    int iterations = 100; // Number of repetitions

    float *input = (float *)malloc(inputRows * inputCols * sizeof(float));
    float *kernel = (float *)malloc(kernelRows * kernelCols * sizeof(float));
    float *output = (float *)malloc(inputRows * inputCols * sizeof(float));

    srand(time(0));
    for (int i = 0; i < inputRows * inputCols; i++) {
        input[i] = rand() % 10 + 1;
    }
    for (int i = 0; i < kernelRows * kernelCols; i++) {
        kernel[i] = (rand() % 5 + 1) / 5.0f;
    }

    struct timeval start, end;
    gettimeofday(&start, NULL);
    for (int iter = 0; iter < iterations; iter++) {
        convolve2D(input, kernel, output, inputRows, inputCols, kernelRows, kernelCols);
    }
    gettimeofday(&end, NULL);

    double elapsed = ((end.tv_sec - start.tv_sec) * 1000.0) + ((end.tv_usec - start.tv_usec) / 1000.0);
    elapsed /= iterations; // Average time per iteration in milliseconds
    printf("2D Convolution completed.\n");
    printf("Average time per iteration: %f milliseconds\n", elapsed);

    free(input);
    free(kernel);
    free(output);

    return 0;
}
