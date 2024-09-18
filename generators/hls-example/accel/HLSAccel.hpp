#ifndef _GCD_EX_H_
#define _GCD_EX_H_

#include <iostream>
#include <ap_int.h>
#include <hls_stream.h>

#define DATA_WIDTH 32

typedef ap_uint<DATA_WIDTH> io_t;

io_t HLSAccelBlackBox(io_t x, io_t y);

#endif