#!/bin/bash

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

export PATH=$LOCAL_VERILATOR_DIR/install/bin:$PATH
export FIRESIM_ENV_SOURCED=1

SIMULATION_ARGS="${mapping[$1]}"

run_test_suite () {
    make -C $LOCAL_FIRESIM_DIR $SIMULATION_ARGS run-${1}-tests-fast
}

run_test_suite bmark
run_test_suite nic
run_test_suite blockdev
