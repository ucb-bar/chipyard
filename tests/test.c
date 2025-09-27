#include"cordic.h"
#include<stdio.h>
#include<stdbool.h>
#include<math.h>
#define CORDIC_SCALE (32768.0f / 3.1415926f) // 32768 / π
#define N 5
#define PI 3.14159265358979323846
int main() {
    printf("CORDIC Test Program\n");
    float sin_val, cos_val;
    float x = 1, y = 0;
    float theta;
    printf("y       ,x       ,expect_angle ,acc_arctan_val, acc_arctan_cycle ,arctan_std_val,arctan_std_cycle\n");
    for(int i = 0;i < 360;i = i + 15){
        float theta_test = i * PI / 180.0f; // 角度轉弧度
        x = 1000 * cosf(theta_test);
        y = 1000 * sinf(theta_test);
        printf("%.6f,%.6f, %.6f ",y / 1000,x / 1000, theta_test * 180 / PI);

        RISCVarctan(CORDIC_ROCC,x,y, &theta, true);
        RISCVarctan(NONE, x, y, &theta, true);

    }
    printf("degrees, sinf_val, sinf_cycle, cosf_val, cosf_cycle, \
            MMIO_sin_val, MMIO_sin_cycle, MMIO_cos_val, MMIO_cos_cycle,\
            ROCC_sin_val, ROCC_sin_cycle, ROCC_cos_val, ROCC_cos_cycle, \
            \n");
    for(int i = 0; i < 360; i += 10) {
        float theta_rad = i * (3.1415926f / 180.0f); // 角度轉弧度
        
        printf("%d, ", i);
        
        // 使用標準函數
        RISCVsin(NONE, theta_rad, &sin_val, true);
        RISCVcos(NONE, theta_rad, &cos_val,true);
        
        // 使用 MMIO CORDIC
        RISCVsin(CORDIC_MMIO, theta_rad, &sin_val, true);
        RISCVcos(CORDIC_MMIO, theta_rad, &cos_val,true);
        
        // 使用 ROCC CORDIC
        RISCVsin(CORDIC_ROCC, theta_rad, &sin_val, true);
        RISCVcos(CORDIC_ROCC, theta_rad, &cos_val,true);
    }   
    return 0;
}