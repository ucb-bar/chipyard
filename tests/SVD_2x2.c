
#include <stdio.h>
#include <stdint.h>
#include <unistd.h> // for usleep()
#include "mmio.h"
#include <math.h>
#include "rocc.h"
#include "cordic.h"
#include <stdbool.h>

#define EPSILON 1e-10
#define MAX_ITER 100
#define PI 3.14159265358979323846
static inline uint64_t rdcycle() {
    uint64_t cycle;
    asm volatile ("rdcycle %0" : "=r"(cycle));
    return cycle;
}

void jacobi_2x2(float A[2][2], float eigval[2], float eigvec[2][2],int CORDIC) {
    //uint64_t start = rdcycle();
    float theta;
    RISCVarctan(CORDIC,(A[0][0] - A[1][1]),(2* A[0][1]), &theta, false);
    theta = 0.5 * theta;
    float c = 0, s = 0;
    RISCVsin(CORDIC,(theta), &s,false);
    RISCVcos(CORDIC,(theta), &c,false);
    eigvec[0][0] = c;
    eigvec[0][1] = -s;
    eigvec[1][0] = s;
    eigvec[1][1] = c;

    eigval[0] = c * c * A[0][0] + 2 * c * s * A[0][1] + s * s * A[1][1];
    eigval[1] = s * s * A[0][0] - 2 * c * s * A[0][1] + c * c * A[1][1];
    //uint64_t end = rdcycle();
    //printf("Jacobi rotation cycles: %lu\n", end - start);
}

// 對任意 2x2 矩陣 A，做 SVD：只求出 U, Σ, V
void svd_2x2(float A[2][2],float Ainv[2][2],bool CORDIC) {
    uint64_t t1 = rdcycle();

    // Step 1: 計算 A^T A（2x2）
    float ATA[2][2];
    ATA[0][0] = A[0][0]*A[0][0] + A[1][0]*A[1][0];
    ATA[0][1] = A[0][0]*A[0][1] + A[1][0]*A[1][1];
    ATA[1][0] = ATA[0][1];
    ATA[1][1] = A[0][1]*A[0][1] + A[1][1]*A[1][1];
    uint64_t t2 = rdcycle();

    // Step 2: 用 Jacobi 對 ATA 對角化，得到 V 和 Σ^2
    float eigval[2], V[2][2];
    jacobi_2x2(ATA, eigval, V, CORDIC);
    uint64_t t3 = rdcycle();

    // Step 3: 奇異值是特徵值平方根（可能需要排序）
    float sigma[2];
    sigma[0] = sqrt(fmax(eigval[0], 0));
    sigma[1] = sqrt(fmax(eigval[1], 0));
    uint64_t t4 = rdcycle();

    // Step 4: 求 U = A * V / σ
    float U[2][2];
    for (int i = 0; i < 2; i++) {
        float temp0 = A[0][0]*V[0][i] + A[0][1]*V[1][i];
        float temp1 = A[1][0]*V[0][i] + A[1][1]*V[1][i];
        if (sigma[i] > EPSILON) {
            U[0][i] = temp0 / sigma[i];
            U[1][i] = temp1 / sigma[i];
        } else {
            U[0][i] = 0;
            U[1][i] = 0;
        }
    }
    uint64_t t5 = rdcycle();
    float invSigma[2];
    invSigma[0] = 1.0f / sigma[0];
    invSigma[1] = 1.0f / sigma[1];

    // Intermediate matrix: invSigma * U^T
    float SUT[2][2];
    for (int i = 0; i < 2; i++) { // row
        for (int j = 0; j < 2; j++) { // col
            SUT[i][j] = invSigma[i] * U[j][i];
        }
    }

    // Final: Ainv = V * SUT
    for (int i = 0; i < 2; i++) {
        for (int j = 0; j < 2; j++) {
            Ainv[i][j] = V[i][0] * SUT[0][j] + V[i][1] * SUT[1][j];
        }
    }

    printf("step1: %lu step2: %lu step3: %lu step4: %lu total: %lu\n", t2-t1, t3-t2, t4-t3, t5-t4, t5 - t1);
    // 印出結果
    printf("Right singular vectors V:\n");
    printf("[%.5f %.5f]\n", V[0][0], V[0][1]);
    printf("[%.5f %.5f]\n", V[1][0], V[1][1]);

    printf("\nSingular values (Σ):\n");
    printf("[%.5f 0]\n", sigma[0]);
    printf("[0 %.5f]\n", sigma[1]);

    printf("\nLeft singular vectors U:\n");
    printf("[%.5f %.5f]\n", U[0][0], U[0][1]);
    printf("[%.5f %.5f]\n", U[1][0], U[1][1]);


    printf("SVD Inverse of A:\n");
    printf("[%.5f %.5f]\n", Ainv[0][0], Ainv[0][1]);
    printf("[%.5f %.5f]\n", Ainv[1][0], Ainv[1][1]);
}

int main() {
    float A[2][2] = {
        {3, 1},
        {3, 2}
    };
    float Ainv[2][2], Binv[2][2], Cinv[2][2];
    printf("\nUsing Without CORDIC:\n");
    svd_2x2(A,Ainv,NONE);
    float B[2][2] = {
        {3, 1},
        {3, 2}
    };
    printf("\nUsing With MMIO_CORDIC:\n");
    svd_2x2(B,Binv,CORDIC_MMIO);
    float C[2][2] = {
        {3, 1},
        {3, 2}
    };
    printf("\nUsing With ROCC_CORDIC:\n");
    svd_2x2(C,Cinv,CORDIC_ROCC);
    return 0;
}
