#!/bin/bash

# create the different verilator builds
# argument is the make command string

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

cd $REMOTE_CHIPYARD_DIR
./scripts/init-submodules-no-riscv-tools.sh

# Test firesim compile and metasim
export FIRESIM_ENV_SOURCED=1
cd $REMOTE_FIRESIM_DIR
make TARGET_PROJECT=firesim EMUL=verilator ${mapping[$1]} run-verilator SIM_BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

