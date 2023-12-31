#!/bin/sh

set -ex

if [ $# -ne 2 ]; then
    echo "usage: $0 config kernelname"
    exit 1
fi
CONFIG=$1
KERNEL=$2

if ! diff ../args.bin ../args.bin.$KERNEL; then
    echo 'args.bin changed, running make clean'
    make CONFIG=$CONFIG clean
fi

../switch-vortex.sh $KERNEL

ls -lh ../args.bin
ls -lh ../op_a.bin
ls -lh ../op_b.bin

make run-binary-debug CONFIG=$CONFIG BINARY=/scratch/hansung/src/vortex2/tests/opencl/$KERNEL/$KERNEL.bin.elf LOADMEM=1
