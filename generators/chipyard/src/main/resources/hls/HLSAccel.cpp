#ifndef _GCD_EX_H_
#define _GCD_EX_H_

#include <ap_int.h>

#define DATA_WIDTH 32

typedef ap_uint<DATA_WIDTH> io_t;

io_t HLSGCDAccelBlackBox(io_t x, io_t y) {
    io_t tmp;
    io_t gcd;

    tmp = y;
    gcd = x;

    while(tmp != 0) {
        if (gcd > tmp) { 
            gcd = gcd - tmp;
        } else { 
            tmp = tmp - gcd;
        }
    }

    return gcd;
}

#endif