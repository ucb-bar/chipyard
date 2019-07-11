#!/bin/bash

# run the different tests

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

export VERILATOR_ROOT=$LOCAL_VERILATOR_DIR/install/share/verilator

run_bmark () {
    make run-bmark-tests-fast -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

run_asm () {
    make run-asm-tests-fast -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

run_both () {
    run_bmark $@
    run_asm $@
}

case $1 in
    example)
        run_bmark ${mapping[$1]}
        ;;
    boomexample)
        run_bmark ${mapping[$1]}
        ;;
    boomrocketexample)
        run_bmark ${mapping[$1]}
        ;;
    boom)
        run_bmark ${mapping[$1]}
        ;;
    rocketchip)
        run_bmark ${mapping[$1]}
        ;;
    hwacha)
        export RISCV=$LOCAL_ESP_DIR
        export LD_LIBRARY_PATH=$LOCAL_ESP_DIR/lib
        make run-rv64uv-p-asm-tests-fst -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR ${mapping[$1]}
        ;;
    *)
        echo "No set of tests for $1. Did you spell it right?"
        exit 1
        ;;
esac
