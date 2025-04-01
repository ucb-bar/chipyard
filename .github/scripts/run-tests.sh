#!/bin/bash

# run the different tests

# turn echo on and error on earliest command
set -ex

# get remote exec variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

DISABLE_SIM_PREREQ="BREAK_SIM_PREREQ=1"
MAPPING_FLAGS=${mapping[$1]}

run_bmark () {
    make run-bmark-tests-fast -j1 -C $LOCAL_SIM_DIR $DISABLE_SIM_PREREQ $MAPPING_FLAGS $@
}

run_asm () {
    make run-asm-tests-fast -j1 -C $LOCAL_SIM_DIR $DISABLE_SIM_PREREQ $MAPPING_FLAGS $@
}

run_tracegen () {
    make tracegen -C $LOCAL_SIM_DIR $DISABLE_SIM_PREREQ $MAPPING_FLAGS $@
}

run_binary () {
    make run-binary-fast -C $LOCAL_SIM_DIR $DISABLE_SIM_PREREQ $MAPPING_FLAGS $@
}

build_tests() {
    (cd $LOCAL_CHIPYARD_DIR/tests && cmake . && make)
}

case $1 in
    chipyard-rocket)
        run_bmark LOADMEM=1
        run_asm LOADMEM=1
        build_tests

        # Test run-binary with and without loadmem
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/hello.riscv LOADMEM=1
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/hello.riscv
        ;;
    chipyard-dmirocket)
        # Test checkpoint-restore without cospike
        # TODO: This is broken on verilator for some reason
        # $LOCAL_CHIPYARD_DIR/scripts/generate-ckpt.sh -b $RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/dhrystone.riscv -i 10000
        # run_binary LOADARCH=$PWD/dhrystone.riscv.0x80000000.unused.10000.defaultspikedts.loadarch EXTRA_SIM_FLAGS="+cospike-enable=0"
        # Test cospike without checkpoint-restore
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/dhrystone.riscv LOADMEM=1
        ;;
    chipyard-boomv3|chipyard-boomv4|chipyard-shuttle|chipyard-spike|chipyard-shuttle3)
        run_asm LOADMEM=1
        run_bmark LOADMEM=1
        ;;
    chipyard-dmiboomv3|chipyard-dmiboomv4)
        # Test checkpoint-restore
        $LOCAL_CHIPYARD_DIR/scripts/generate-ckpt.sh -b $RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/dhrystone.riscv -i 10000
        run_binary LOADARCH=$PWD/dhrystone.riscv.0x80000000.unused.10000.defaultspikedts.loadarch
        ;;
    chipyard-hetero)
        run_bmark LOADMEM=1
        ;;
    chipyard-prefetchers)
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/dhrystone.riscv LOADMEM=1
        ;;
    chipyard-gemmini)
        GEMMINI_SOFTWARE_DIR=$LOCAL_SIM_DIR/../../generators/gemmini/software/gemmini-rocc-tests
        rm -rf $GEMMINI_SOFTWARE_DIR/riscv-tests
        cd $LOCAL_SIM_DIR
        run_binary BINARY=$GEMMINI_SOFTWARE_DIR/build/bareMetalC/aligned-baremetal LOADMEM=1
        run_binary BINARY=$GEMMINI_SOFTWARE_DIR/build/bareMetalC/raw_hazard-baremetal LOADMEM=1
        run_binary BINARY=$GEMMINI_SOFTWARE_DIR/build/bareMetalC/mvin_mvout-baremetal LOADMEM=1
        ;;
    chipyard-mempress)
        (cd $LOCAL_CHIPYARD_DIR/generators/mempress/software/src && make)
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/generators/mempress/software/src/mempress-rocc.riscv LOADMEM=1
        ;;
    chipyard-compressacc)
        (cd $LOCAL_CHIPYARD_DIR/generators/compress-acc/software-zstd/compress && ./build-hcb-single-file.sh)
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/generators/compress-acc/software-zstd/compress/009987_cl0_ws12.riscv LOADMEM=1
        ;;
    chipyard-manymmioaccels)
        build_tests

	# test streaming-passthrough
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/streaming-passthrough.riscv LOADMEM=1

	# test streaming-fir
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/streaming-fir.riscv LOADMEM=1

	# test fft
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/fft.riscv LOADMEM=1
        ;;
    chipyard-nvdla)
        build_tests

        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/nvdla.riscv LOADMEM=1
        ;;
    chipyard-manyperipherals)
        # SPI Flash read tests
        build_tests

        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/spiflashread.riscv
        ;;
    chipyard-spiflashwrite)
        build_tests

        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/spiflashwrite.riscv LOADMEM=1
        [[ "`xxd $LOCAL_CHIPYARD_DIR/tests/spiflash.img  | grep 1337\ 00ff\ aa55\ face | wc -l`" == "6" ]] || false
        ;;
    chipyard-tethered)
        build_tests

        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/hello.riscv LOADMEM=1 EXTRA_SIM_FLAGS="+cflush_addr=0x2010200"
        ;;
    chipyard-symmetric)
        build_tests

        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/symmetric.riscv LOADMEM=1 EXTRA_SIM_FLAGS="+offchip_sel=0"
	run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/symmetric.riscv LOADMEM=1 EXTRA_SIM_FLAGS="+offchip_sel=1"
        ;;
    chipyard-llcchiplet)
        build_tests

        run_binary BINARY=$LOCAL_CHIPYARD_DIR/tests/hello.riscv LOADMEM=1
        ;;
    chipyard-rerocc)
        make -C $LOCAL_CHIPYARD_DIR/generators/rerocc/software
        run_binary BINARY=$LOCAL_CHIPYARD_DIR/generators/rerocc/software/test.riscv LOADMEM=1
        ;;
    chipyard-rocketvector|chipyard-shuttlevector)
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-sgemm.riscv LOADMEM=1
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-strcmp.riscv LOADMEM=1
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-daxpy.riscv LOADMEM=1
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-memcpy.riscv LOADMEM=1
        ;;
    chipyard-shuttleara)
        # Ara does not work with verilator
        # run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-sgemm.riscv LOADMEM=1
        # Ara cannot run strcmp
        # run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-strcmp.riscv LOADMEM=1
        # run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-daxpy.riscv LOADMEM=1
        # run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/vec-memcpy.riscv LOADMEM=1
        ;;
    tracegen)
        run_tracegen
        ;;
    tracegen-boomv3)
        run_tracegen
        ;;
    tracegen-boomv4)
        run_tracegen
        ;;
    chipyard-cva6)
        run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/multiply.riscv
        ;;
    chipyard-ibex)
        # Ibex cannot run the riscv-tests binaries for some reason
        # run_binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv32ui-p-simple
        ;;
    chipyard-vexiiriscv)
        run_asm LOADMEM=1
        run_bmark LOADMEM=1
        ;;
    chipyard-sodor)
        run_asm
        ;;
    chipyard-constellation)
        run_binary LOADMEM=1 BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/dhrystone.riscv
        ;;
    chipyard-tacit-rocket)
        run_binary LOADMEM=1 BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/dhrystone.riscv
        ;;
    chipyard-zephyr)
        run_binary LOADMEM=1 BINARY=$LOCAL_CHIPYARD_DIR/software/zephyrproject/zephyr/build/zephyr/zephyr.elf
        ;;
    chipyard-radiance)
        # Verilator fails to build sim binary, just generate verilog
        ;;
    icenet)
        run_binary BINARY=none
        ;;
    testchipip)
        run_binary BINARY=none
        ;;
    constellation)
        run_binary BINARY=none
        ;;
    rocketchip-amba)
        run_binary BINARY=none
        ;;
    rocketchip-tlsimple)
        run_binary BINARY=none
        ;;
    rocketchip-tlwidth)
        run_binary BINARY=none
        ;;
    rocketchip-tlxbar)
        run_binary BINARY=none
        ;;
    *)
        echo "No set of tests for $1. Did you spell it right?"
        exit 1
        ;;
esac
