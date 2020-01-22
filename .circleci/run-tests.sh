#!/bin/bash

# run the different tests

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

export VERILATOR_ROOT=$LOCAL_VERILATOR_DIR/install/share/verilator

run_bmark () {
    make run-bmark-tests-fast -j$NPROC -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

run_asm () {
    make run-asm-tests-fast -j$NPROC -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

run_both () {
    run_bmark $@
    run_asm $@
}

run_tracegen () {
    make tracegen -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

case $1 in
    chipyard-rocket)
        run_bmark ${mapping[$1]}
        ;;
    chipyard-boom)
        run_bmark ${mapping[$1]}
        ;;
    chipyard-hetero)
        run_bmark ${mapping[$1]}
        ;;
    rocketchip)
        run_bmark ${mapping[$1]}
        ;;
    chipyard-hwacha)
        export RISCV=$LOCAL_ESP_DIR
        export LD_LIBRARY_PATH=$LOCAL_ESP_DIR/lib
        export PATH=$RISCV/bin:$PATH
        make run-rv64uv-p-asm-tests -j$NPROC -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR ${mapping[$1]}
        ;;
    chipyard-gemmini)
        export RISCV=$LOCAL_ESP_DIR
        export LD_LIBRARY_PATH=$LOCAL_ESP_DIR/lib
        export PATH=$RISCV/bin:$PATH
        GEMMINI_SOFTWARE_DIR=$LOCAL_SIM_DIR/../../generators/gemmini/software/gemmini-rocc-tests
        cd $GEMMINI_SOFTWARE_DIR
        ./build.sh
        cd $LOCAL_SIM_DIR
        make run-binary ${mapping[$1]} BINARY=$GEMMINI_SOFTWARE_DIR/build/bareMetalC/aligned-baremetal
        make run-binary ${mapping[$1]} BINARY=$GEMMINI_SOFTWARE_DIR/build/bareMetalC/raw_hazard-baremetal
        make run-binary ${mapping[$1]} BINARY=$GEMMINI_SOFTWARE_DIR/build/bareMetalC/mvin_mvout-baremetal
        ;;
    tracegen)
        run_tracegen ${mapping[$1]}
        ;;
    tracegen-boom)
        run_tracegen ${mapping[$1]}
        ;;
    *)
        echo "No set of tests for $1. Did you spell it right?"
        exit 1
        ;;
esac
