#!/bin/bash

set -ex

cd sims/vsim/

# no hwacha tests for right now

BCONFIG=MegaBeagleSimConfig

# test asm/bmark tests
make CONFIG=$BCONFIG
make CONFIG=$BCONFIG run-asm-tests
make CONFIG=$BCONFIG run-bmark-tests

# test with systolic array
TEST_DIR=$(pwd)/tests/beagle-systolic-tests
tests=("matmul", "matmul_os", "matmul_ws", "mvin_mvout", "mvin_mvout_acc", "mvin_mvout_acc_stride", "mvin_mvout_stride", "raw_hazard", "tiled_matmul_os", "tiled_matmul_ws")
for t in "${tests[@]}"
do
    echo "Running program $t in $TEST_DIR"
    make CONFIG=$BCONFIG BINARY=$TEST_DIR/$t run-binary
done
