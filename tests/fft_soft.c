#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <unistd.h> // for usleep()
#include "mmio.h"
#include "rocc.h"
#include "cordic.h"
#include <math.h>

#define PI 3.14159265358979323846
#define N 8
static inline uint64_t rdcycle() {
    uint64_t cycle;
    asm volatile ("rdcycle %0" : "=r"(cycle));
    return cycle;
}
typedef struct {
    float real;
    float imag;
} Complex;
static inline float wrap_to_2pi(float theta) {
    // fmodf(theta, 2π) → 把角度縮到 (-2π, 2π)
    theta = fmodf(theta, 2.0f * PI);
    if (theta < 0)
        theta += 2.0f * PI; // 把負角度補成正的
    return theta; // 範圍：[0, 2π)
}

void fft(Complex *x,int CORDIC) {
    uint64_t start = rdcycle();
    // Bit reversal permutation
    int j = 0;
    for (int i = 0; i < N; i++) {
        if (i < j) {
            Complex temp = x[i];
            x[i] = x[j];
            x[j] = temp;
        }
        int m = N >> 1;
        while (m >= 1 && j >= m) {
            j -= m;
            m >>= 1;
        }
        j += m;
    }

    // Cooley-Tukey
    for (int s = 1; s <= 3; s++) { // log2(N) = 3
        int m = 1 << s;
        float theta = wrap_to_2pi(-2 * PI / m);
        float cos = 0,sin = 0;
        RISCVsin(CORDIC,theta, &sin,false);
        RISCVcos(CORDIC,theta, &cos,false);
        Complex wm = { cos, sin};
        for (int k = 0; k < N; k += m) {
            Complex w = {1.0, 0.0};
            for (int j = 0; j < m / 2; j++) {
                Complex t, u;
                t.real = w.real * x[k + j + m/2].real - w.imag * x[k + j + m/2].imag;
                t.imag = w.real * x[k + j + m/2].imag + w.imag * x[k + j + m/2].real;

                u = x[k + j];

                x[k + j].real = u.real + t.real;
                x[k + j].imag = u.imag + t.imag;
                x[k + j + m/2].real = u.real - t.real;
                x[k + j + m/2].imag = u.imag - t.imag;

                // w = w * wm
                float w_real = w.real * wm.real - w.imag * wm.imag;
                float w_imag = w.real * wm.imag + w.imag * wm.real;
                w.real = w_real;
                w.imag = w_imag;
            }
        }
    }
    uint64_t end = rdcycle();
    printf("FFT cycles: %lu\n", end - start);

}

int main() {
    Complex input[N] = {
        { 0.707, -0.707 },
        { 0.000,  0.996 },
        {-0.707, -0.707 },
        {-1.000,  0.000 },
        {-0.707,  0.707 },
        { 0.000,  0.004 },
        { 0.707,  0.707 },
        { 1.000,  0.000 },
    };
    printf("using Standard function:\n");
    fft(input, 0);
    printf("FFT result:\n");
    for (int i = 0; i < N; i++) {
        printf("[%d] %.4f ", i, input[i].real);
        if(input[i].imag > 0)
            printf("+");
        else 
            printf("-");
        printf(" j%.4f\n", fabs(input[i].imag));
    }
    ///////Using MMIO
    Complex input1[N] = {
        { 0.707, -0.707 },
        { 0.000,  0.996 },
        {-0.707, -0.707 },
        {-1.000,  0.000 },
        {-0.707,  0.707 },
        { 0.000,  0.004 },
        { 0.707,  0.707 },
        { 1.000,  0.000 },
    };
    printf("using CORDIC_MMIO function:\n");
    fft(input1, 1); 
        printf("FFT result:\n");
    for (int i = 0; i < N; i++) {
        printf("[%d] %.4f ", i, input1[i].real);
        if(input1[i].imag > 0)
            printf("+");
        else 
            printf("-");
        printf(" j%.4f\n", fabs(input1[i].imag));
    }
    ///////Using ROCC
        Complex input2[N] = {
        { 0.707, -0.707 },
        { 0.000,  0.996 },
        {-0.707, -0.707 },
        {-1.000,  0.000 },
        {-0.707,  0.707 },
        { 0.000,  0.004 },
        { 0.707,  0.707 },
        { 1.000,  0.000 },
    };
    printf("using CORDIC_ROCC function:\n");
    fft(input2, 2); 
        printf("FFT result:\n");
    for (int i = 0; i < N; i++) {
        printf("[%d] %.4f ", i, input2[i].real);
        if(input2[i].imag > 0)
            printf("+");
        else 
            printf("-");
        printf(" j%.4f\n", fabs(input2[i].imag));
    }
    return 0;
}
