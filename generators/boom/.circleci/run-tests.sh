#!/bin/bash

#-------------------------------------------------------------
# run the different tests (based on the config)
#
# run location: circle ci docker image
# usage:
#   $1 - config string
#-------------------------------------------------------------

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

run_bmark () {
    make -j$CI_MAKE_NPROC run-bmark-tests-fast -C $LOCAL_SIM_DIR $@
}

run_asm () {
    make -j$CI_MAKE_NPROC run-asm-tests-fast -C $LOCAL_SIM_DIR $@
}

run_both () {
    run_bmark $@
    run_asm $@
}

case $1 in
    smallboom)
        run_both ${mapping[$1]}
        ;;
    mediumboom)
        run_both ${mapping[$1]}
        ;;
    largeboom)
        run_bmark ${mapping[$1]}
        ;;
    megaboom)
        run_bmark ${mapping[$1]}
        ;;
    boomandrocket)
        run_both ${mapping[$1]}
        ;;
    rv32boom)
        run_asm ${mapping[$1]}
        ;;
    hwachaboom)
        export RISCV=$LOCAL_ESP_DIR
        export LD_LIBRARY_PATH=$LOCAL_ESP_DIR/lib
        export PATH=$RISCV/bin:$PATH
        make run-rv64uv-p-asm-tests -j$CI_MAKE_NPROC -C $LOCAL_SIM_DIR ${mapping[$1]}
        ;;
    *)
        echo "No set of tests for $1. Did you spell it right?"
        exit 1
        ;;
esac
