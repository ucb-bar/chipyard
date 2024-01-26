#!/bin/bash

# copy gpu binaries from stimuli folder
# usage:
#   copy-gpu-binaries.sh

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

echo "$0: REMOTE_CHIPYARD_DIR=$REMOTE_CHIPYARD_DIR"
cd $REMOTE_CHIPYARD_DIR

cp -av $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.bin.elf \
       $REMOTE_CHIPYARD_DIR/sims/
cp -av $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.args.size64.romAddr.core1.bin \
       $REMOTE_CHIPYARD_DIR/sims/args.bin
cp -av $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.input.a.size64.bin \
       $REMOTE_CHIPYARD_DIR/sims/op_a.bin
cp -av $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.input.b.size64.bin \
       $REMOTE_CHIPYARD_DIR/sims/op_b.bin
