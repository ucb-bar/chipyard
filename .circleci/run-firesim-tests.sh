#!/bin/bash

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

export PATH=$LOCAL_VERILATOR_DIR/install/bin:$PATH
export FIRESIM_ENV_SOURCED=1

SIMULATION_ARGS="${mapping[$1]}"

cd $LOCAL_CHIPYARD_DIR/sims/firesim
./scripts/build-libelf.sh
./scripts/build-libdwarf.sh
cd $LOCAL_CHIPYARD_DIR


run_test_suite () {
    export RISCV=$LOCAL_RISCV_DIR
    export LD_LIBRARY_PATH=$LOCAL_RISCV_DIR/lib
    make -C $LOCAL_FIRESIM_DIR $SIMULATION_ARGS run-${1}-tests-fast
}


run_test_suite bmark
run_test_suite nic
run_test_suite blockdev
