#!/bin/bash

#################
# TESTING SCRIPT
#################

set -ex

cd sims/vcs/

echo ">>>Make sure you are using RISCV=.../esp-tools<<<"
echo ">>>>>This will break if you are using the default toolchain<<<<<"
echo "If not already done..."
echo "    Run \`./scripts/build-toolchain.sh esp-tools\` to build an esp-tools toolchain"
echo "    Then run \`source env.sh\` to setup RISCV, LD_LIBRARY_PATH, PATH"
echo "If esp-tools already built..."
echo "    Run \`source env.sh\` with the adjusted path to your prebuilt esp-tools"

: ${BCONFIG:=MegaBeagleConfig} # default value of the config

# change the config if not specified
if [ $# -ne 0 ]; then
  BCONFIG=$1
fi

# test asm/bmark tests (includes hwacha tests (asm/bmark))
make CONFIG=$BCONFIG
make CONFIG=$BCONFIG run-asm-tests-fast
make CONFIG=$BCONFIG run-bmark-tests-fast

# test individual binaries with the systolic array (assumes sys. is on hart1)
TEST_DIR=$(pwd)/../../tests/beagle-systolic-tests
tests=("matmul" "matmul_os" "matmul_ws" "mvin_mvout" "mvin_mvout_acc" "mvin_mvout_acc_stride" "mvin_mvout_stride" "raw_hazard" "tiled_matmul_os" "tiled_matmul_ws")
for t in "${tests[@]}"
do
    echo "Running program $t in $TEST_DIR"
    make CONFIG=$BCONFIG BINARY=$TEST_DIR/$t run-binary
done
