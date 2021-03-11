#!/bin/bash

# run the different tests

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

run_bmark () {
    make run-bmark-tests-fast -j$CI_MAKE_NPROC -C $LOCAL_SIM_DIR $@
}

run_asm () {
    make run-asm-tests-fast -j$CI_MAKE_NPROC -C $LOCAL_SIM_DIR $@
}

run_both () {
    run_bmark $@
    run_asm $@
}

run_tracegen () {
    make tracegen -C $LOCAL_SIM_DIR $@
}

# TODO BUG: the run-binary command forces a rebuild of the simulator in CI
#           instead, directly run the simulator binary
case $1 in
    chipyard-rocket)
        run_bmark ${mapping[$1]}
        ;;
    chipyard-dmirocket)
        run_bmark ${mapping[$1]}
        ;;
    chipyard-lbwif)
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
        make run-rv64uv-p-asm-tests -j$CI_MAKE_NPROC -C $LOCAL_SIM_DIR ${mapping[$1]}
        ;;
    chipyard-gemmini)
        export RISCV=$LOCAL_ESP_DIR
        export LD_LIBRARY_PATH=$LOCAL_ESP_DIR/lib
        export PATH=$RISCV/bin:$PATH
        GEMMINI_SOFTWARE_DIR=$LOCAL_SIM_DIR/../../generators/gemmini/software/gemmini-rocc-tests
        rm -rf $GEMMINI_SOFTWARE_DIR/riscv-tests
        cd $LOCAL_SIM_DIR
        $LOCAL_SIM_DIR/simulator-chipyard-GemminiRocketConfig $GEMMINI_SOFTWARE_DIR/build/bareMetalC/aligned-baremetal
        $LOCAL_SIM_DIR/simulator-chipyard-GemminiRocketConfig $GEMMINI_SOFTWARE_DIR/build/bareMetalC/raw_hazard-baremetal
        $LOCAL_SIM_DIR/simulator-chipyard-GemminiRocketConfig $GEMMINI_SOFTWARE_DIR/build/bareMetalC/mvin_mvout-baremetal
        ;;
    chipyard-sha3)
        (cd $LOCAL_CHIPYARD_DIR/generators/sha3/software && ./build.sh)
        $LOCAL_SIM_DIR/simulator-chipyard-Sha3RocketConfig $LOCAL_CHIPYARD_DIR/generators/sha3/software/benchmarks/bare/sha3-rocc.riscv
        ;;
    chipyard-streaming-passthrough)
        make -C $LOCAL_CHIPYARD_DIR/tests
        $LOCAL_SIM_DIR/simulator-chipyard-StreamingPassthroughRocketConfig $LOCAL_CHIPYARD_DIR/tests/streaming-passthrough.riscv
        ;;
    chipyard-streaming-fir)
        make -C $LOCAL_CHIPYARD_DIR/tests
        $LOCAL_SIM_DIR/simulator-chipyard-StreamingFIRRocketConfig $LOCAL_CHIPYARD_DIR/tests/streaming-fir.riscv
        ;;
    chipyard-spiflashread)
        make -C $LOCAL_CHIPYARD_DIR/tests
        make -C $LOCAL_SIM_DIR ${mapping[$1]} BINARY=$LOCAL_CHIPYARD_DIR/tests/spiflashread.riscv SIM_FLAGS="+spiflash0=${LOCAL_CHIPYARD_DIR}/tests/spiflash.img" run-binary
        ;;
    chipyard-spiflashwrite)
        make -C $LOCAL_CHIPYARD_DIR/tests
        make -C $LOCAL_SIM_DIR ${mapping[$1]} BINARY=$LOCAL_CHIPYARD_DIR/tests/spiflashwrite.riscv SIM_FLAGS="+spiflash0=${LOCAL_CHIPYARD_DIR}/tests/spiflash.img" run-binary
        [[ "`xxd $LOCAL_CHIPYARD_DIR/tests/spiflash.img  | grep 1337\ 00ff\ aa55\ face | wc -l`" == "6" ]] || false
        ;;
    tracegen)
        run_tracegen ${mapping[$1]}
        ;;
    tracegen-boom)
        run_tracegen ${mapping[$1]}
        ;;
    chipyard-cva6)
        make run-binary-fast -C $LOCAL_SIM_DIR ${mapping[$1]} BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/multiply.riscv
        ;;
    chipyard-sodor)
        run_asm ${mapping[$1]}
        ;;
    chipyard-nvdla)
        make -C $LOCAL_CHIPYARD_DIR/tests
        make -C $LOCAL_SIM_DIR ${mapping[$1]} BINARY=$LOCAL_CHIPYARD_DIR/tests/nvdla.riscv run-binary
        ;;
    icenet)
        make run-binary-fast BINARY=none -C $LOCAL_SIM_DIR ${mapping[$1]}
        ;;
    testchipip)
        make run-binary-fast BINARY=none -C $LOCAL_SIM_DIR ${mapping[$1]}
        ;;
    *)
        echo "No set of tests for $1. Did you spell it right?"
        exit 1
        ;;
esac
