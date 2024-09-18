#include "HLSAccel.hpp"

io_t HLSAccelBlackBox(io_t x, io_t y) {
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