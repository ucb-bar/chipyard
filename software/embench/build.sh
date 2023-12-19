#!/usr/bin/env bash

set -e

echo  "Building embench-iot for riscv64"
BUILDDIR=$(pwd)/build
mkdir -p $BUILDDIR

cd embench-iot
# use the riscv32 target, but use riscv64 compiler
./build_all.py --arch riscv32 --chip generic --board ri5cyverilator --cc riscv64-unknown-elf-gcc --cflags="-c -O2 -ffunction-sections -mabi=lp64d -specs=htif_nano.specs" --ldflags="-Wl,-gc-sections -specs=htif_nano.specs" --user-libs="-lm" --clean -v

echo "Copying binaries to $BUILDDIR"
bmarks=("aha-mont64" "crc32" "cubic" "edn" "huffbench"
        "matmult-int" "minver" "nbody" "nettle-aes"
        "nettle-sha256" "nsichneu" "picojpeg"
        "qrduino" "sglib-combined" "slre" "st"
        "statemate" "ud" "wikisort")
for bmark in "${bmarks[@]}"
do
    cp bd/src/$bmark/$bmark $BUILDDIR/
done
