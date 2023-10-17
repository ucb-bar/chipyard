#!/bin/bash

# copy gpu binaries from stimuli folder
# usage:
#   copy-gpu-binaries.sh

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

cd $REMOTE_CHIPYARD_DIR

cp -a $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.bin.elf \
      $REMOTE_SIM_DIR/
cp -a $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.args.size64.romAddr.bin \
      $REMOTE_SIM_DIR/args.bin
cp -a $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.input.a.size64.bin \
      $REMOTE_SIM_DIR/op_a.bin
cp -a $REMOTE_CHIPYARD_DIR/generators/rocket-gpu/stimuli/vecadd.input.b.size64.bin \
      $REMOTE_SIM_DIR/op_b.bin
