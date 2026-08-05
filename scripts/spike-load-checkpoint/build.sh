#!/bin/bash

set -ex

g++ -O3 -std=c++17 -I$RISCV/include \
	-L$RISCV/lib \
	-Wl,-rpath,$RISCV/lib \
	-lriscv \
	-lfesvr \
	-ldl \
        spike-main.cc -o spike-main.x86
