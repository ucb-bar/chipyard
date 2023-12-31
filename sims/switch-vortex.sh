#!/bin/sh

if [ $# -ne 1 ]; then
    echo "usage: $0 kernelname"
    exit 1
fi
KERNEL=$1

SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
cd $SCRIPT_DIR

if ! [ -f "args.bin.$KERNEL" ]; then
    echo "error: args.bin.$KERNEL not found"
    exit 1
fi

cp -Pv args.bin{.$KERNEL,}
cp -Pv op_a.bin{.$KERNEL,}
cp -Pv op_b.bin{.$KERNEL,}
