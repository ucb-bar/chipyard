#include <stdio.h>
#include <stdint.h>
#include <unistd.h> // for usleep()
#include "mmio.h"
#include "rocc.h"
#include <stdbool.h>
#include <math.h>
#define CORDIC_BASE      0x4000
#define CORDIC_STATUS    (CORDIC_BASE + 0x00)
#define CORDIC_THETA     (CORDIC_BASE + 0x04)
#define CORDIC_SIN_COS   (CORDIC_BASE + 0x08)
#define CORDIC_FUNCT_ARCTAN 0x02
#define CORDIC_FUNCT_COS 0x01
#define CORDIC_FUNCT_SIN 0x00
#define CORDIC_SCALE (32768.0f / 3.1415926f) // 32768 / π
#define PI 3.14159265358979323846
inline  uint64_t  rdcycle() {
     uint64_t cycle;
    asm volatile ("rdcycle %0" : "=r"(cycle));
    return cycle;
}
inline void cosrocc(uint32_t theta_rad, float *cos_val) {
     uint64_t result;
    ROCC_INSTRUCTION_DS(3, result, theta_rad, CORDIC_FUNCT_COS);
    int16_t raw = (int16_t)(result & 0xFFFF);
    *cos_val = raw / 32768.0f;
}

inline void sinrocc(uint32_t theta_rad, float *sin_val) {
     uint64_t    result;
    ROCC_INSTRUCTION_DS(3, result, theta_rad, CORDIC_FUNCT_SIN);
    int16_t raw = (int16_t)(result & 0xFFFF);
    *sin_val = raw / 32768.0f;
}
typedef enum {
    NONE = 0,
    CORDIC_MMIO = 1,
    CORDIC_ROCC = 2
}CORDIC_ENABLE;
void arctanROCC(uint16_t x,uint16_t y, float *theta_val) {
     uint16_t result;
    ROCC_INSTRUCTION_DSS(3, result, x, y, CORDIC_FUNCT_ARCTAN);
    *theta_val =((float)result) / CORDIC_SCALE; 
}
void cosmmio(uint32_t theta_rad, float *cos_val){
    while ((reg_read32(CORDIC_STATUS) & 0x2) == 0) {
    }
    reg_write32(CORDIC_THETA, theta_rad);
    while ((reg_read32(CORDIC_STATUS) & 0x1) == 0) {
    }
    uint32_t cos_sin_val = reg_read32(CORDIC_SIN_COS);
    uint32_t cos_val_raw = (cos_sin_val) & 0xFFFF;
    *cos_val = ((int16_t)cos_val_raw) / 32768.0f; 
}
void sinmmio(uint32_t theta_rad, float *sin_val){
    while ((reg_read32(CORDIC_STATUS) & 0x2) == 0) {
    }
    reg_write32(CORDIC_THETA, theta_rad);
    while ((reg_read32(CORDIC_STATUS) & 0x1) == 0) {
    }
    uint32_t cos_sin_val = reg_read32(CORDIC_SIN_COS);
    uint32_t cos_val_raw = (cos_sin_val >> 16) & 0xFFFF;
    *sin_val = ((int16_t)cos_val_raw) / 32768.0f; 
}
void RISCVsin(CORDIC_ENABLE CORDIC,float theta_rad, float *sin_val, bool print_enable){
    uint64_t start = rdcycle();
    if(CORDIC == CORDIC_MMIO){
        sinmmio((uint32_t)(theta_rad * CORDIC_SCALE), sin_val);
    }else if(CORDIC == CORDIC_ROCC){
        sinrocc((uint32_t)(theta_rad * CORDIC_SCALE), sin_val);
    }else{
        *sin_val = sinf(theta_rad);
    }
    uint64_t end = rdcycle();
    if(print_enable){
        if(CORDIC == CORDIC_MMIO){
            printf(" %.6f, %lu, ", *sin_val, end - start);
        }else if(CORDIC == CORDIC_ROCC){
            printf("%.6f, %lu, ", *sin_val, end - start);
        }else{
            printf("%.6f, %lu, ", *sin_val, end - start);
        }
    }
}
void RISCVcos(CORDIC_ENABLE CORDIC,float theta_rad, float *cos_val,bool print_enable){
        uint64_t start = rdcycle();
    if(CORDIC == CORDIC_MMIO){
        cosmmio((uint32_t)(theta_rad * CORDIC_SCALE), cos_val);
    }else if(CORDIC == CORDIC_ROCC){
        cosrocc((uint32_t)(theta_rad * CORDIC_SCALE), cos_val);
    }else{
        *cos_val = cosf(theta_rad);
    }
    uint64_t end = rdcycle();
    if(print_enable){
        if(CORDIC == CORDIC_MMIO){
            printf(" %.6f, %lu, ", *cos_val, end - start);
        }else if(CORDIC == CORDIC_ROCC){
            printf("%.6f, %lu\n", *cos_val, end - start);
        }else{
            printf("%.6f, %lu, ", *cos_val, end - start);
        }
    }
}
void RISCVarctan(CORDIC_ENABLE CORDIC,float x,float y, float *theta_val,bool print_enable){
    uint64_t start = rdcycle();
    if(CORDIC == CORDIC_ROCC){
        arctanROCC((int16_t)x,(int16_t)y,theta_val);
    }else{
        *theta_val = atan2f(y, x);
        if(*theta_val < 0) {
            *theta_val += 2 * PI; 
        }
    }
    uint64_t end = rdcycle();
    if(print_enable){
        printf(", %.6f, %lu", ((*theta_val) * 180 / PI), end - start);
        if(CORDIC == NONE)
            printf("\n");
    }
}
