#!/bin/bash

set -ex
set -euo pipefail

cd sims/vsim/

echo "This only works if the riscv and esp tools are pointed to correctly"

RISCV_DIR=$1
ESP_DIR=$2
BCONFIG=MegaBeagleSimConfig
#
## test normal asm tests
export RISCV=$RISCV_DIR
export LD_LIBRARY_LIB=$RISCV_DIR/lib
export PATH=$RISCV/bin:$PATH

#make CONFIG=$BCONFIG && make CONFIG=$BCONFIG run-asm-tests-fast
#make CONFIG=$BCONFIG && make CONFIG=$BCONFIG run-bmark-tests

# test with esp-tools (aka test hwacha)
export RISCV=$ESP_DIR
export LD_LIBRARY_LIB=$ESP_DIR/lib
export PATH=$RISCV/bin:$PATH

make CONFIG=$BCONFIG clean # need to rebuild so that the RTL can use esp-tools binaries (includes hwacha)
make CONFIG=$BCONFIG && make CONFIG=$BCONFIG run-bmark-tests

# test with systolic array
TEST_DIR=$(pwd)/tests/beagle-systolic-tests
tests=("matmul", "matmul_os", "matmul_ws", "mvin_mvout", "mvin_mvout_acc", "mvin_mvout_acc_stride", "mvin_mvout_stride", "raw_hazard", "tiled_matmul_os", "tiled_matmul_ws")
for t in "${tests[@]}"
do
    echo "Running program $t in $TEST_DIR"
    make CONFIG=$BCONFIG BINARY=$TEST_DIR/$t run-binary
done
