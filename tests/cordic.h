// cordic.h
#ifndef CORDIC_H
#define CORDIC_H

#include <stdint.h>
#include <stdbool.h>
#ifdef __cplusplus
extern "C" {
#endif

// 共用常數/位址
#define CORDIC_BASE      0x4000
#define CORDIC_STATUS    (CORDIC_BASE + 0x00)
#define CORDIC_THETA     (CORDIC_BASE + 0x04)
#define CORDIC_SIN_COS   (CORDIC_BASE + 0x08)
#define CORDIC_FUNCT_COS 0x01
#define CORDIC_FUNCT_SIN 0x00
#define CORDIC_SCALE (32768.0f / 3.1415926f)

typedef enum {
    NONE = 0,
    CORDIC_MMIO = 1,
    CORDIC_ROCC = 2
} CORDIC_ENABLE;
void arctanROCC(int16_t x,int16_t y, float *theta_val);
void cosrocc(uint32_t theta_rad, float *cos_val);
void sinrocc(uint32_t theta_rad, float *sin_val);
void cosmmio(uint32_t theta_rad, float *cos_val);
void sinmmio(uint32_t theta_rad, float *sin_val);
void RISCVsin(CORDIC_ENABLE en, float theta_rad, float *sin_val,bool print_enable);
void RISCVcos(CORDIC_ENABLE en, float theta_rad, float *cos_val,bool print_enable);
void RISCVarctan(CORDIC_ENABLE CORDIC,float x,float y, float *theta_val,bool print_enable);
#ifdef __cplusplus
}
#endif
#endif
