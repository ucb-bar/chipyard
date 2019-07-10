#!/bin/bash

# run the different tests

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

run_bmark () {
    export VERILATOR_ROOT=$LOCAL_VERILATOR_DIR/install/share/verilator
    make run-bmark-tests-fast -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

run_asm () {
    export VERILATOR_ROOT=$LOCAL_VERILATOR_DIR/install/share/verilator
    make run-asm-tests-fast -C $LOCAL_SIM_DIR VERILATOR_INSTALL_DIR=$LOCAL_VERILATOR_DIR $@
}

run_both () {
    run_bmark $@
    run_asm $@
}

case $1 in
    example)
        run_bmark SUB_PROJECT=example
        ;;
    boomexample)
        run_bmark SUB_PROJECT=example CONFIG=DefaultBoomConfig
        ;;
    boomrocketexample)
        run_bmark SUB_PROJECT=example CONFIG=DefaultBoomAndRocketConfig
        ;;
    boom)
        run_bmark SUB_PROJECT=boom
        ;;
    rocketchip)
        run_bmark SUB_PROJECT=rocketchip
        ;;
    hwacha)
        run_bmark SUB_PROJECT=hwacha
        ;;
    *)
        echo "No set of tests for $1. Did you spell it right?"
        exit 1
        ;;
esac
