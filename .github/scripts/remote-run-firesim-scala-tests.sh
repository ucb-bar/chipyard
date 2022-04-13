#!/bin/bash

# create the different verilator builds
# argument is the make command string

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

export RISCV="$REMOTE_RISCV_DIR"
export LD_LIBRARY_PATH="$RISCV/lib"
export PATH="$RISCV/bin:$PATH"

# Directory locations for handling firesim-local installations of libelf/libdwarf
# This would generally be handled by build-setup.sh/firesim-setup.sh
REMOTE_FIRESIM_SYSROOT=$REMOTE_FIRESIM_DIR/lib-install

cd $REMOTE_CHIPYARD_DIR
./scripts/init-submodules-no-riscv-tools.sh --skip-validate
cd $REMOTE_CHIPYARD_DIR/sims/firesim/sim/firesim-lib/src/main/cc/lib
git submodule update --init elfutils libdwarf
cd $REMOTE_CHIPYARD_DIR/sims/firesim
mkdir -p $REMOTE_FIRESIM_SYSROOT
./scripts/build-libelf.sh $REMOTE_FIRESIM_SYSROOT
./scripts/build-libdwarf.sh $REMOTE_FIRESIM_SYSROOT
cd $REMOTE_CHIPYARD_DIR

make -C $REMOTE_CHIPYARD_DIR/tools/dromajo/dromajo-src/src

TOOLS_DIR=$REMOTE_RISCV_DIR

LD_LIB_DIR=$REMOTE_FIRESIM_SYSROOT/lib:$REMOTE_RISCV_DIR/lib

# Run Firesim Scala Tests
export RISCV=$TOOLS_DIR
export LD_LIBRARY_PATH=$LD_LIB_DIR
export FIRESIM_ENV_SOURCED=1;
export PATH=$REMOTE_VERILATOR_DIR/bin:$PATH
export VERILATOR_ROOT=$REMOTE_VERILATOR_DIR
export COURSIER_CACHE=$REMOTE_WORK_DIR/.coursier-cache
make -C $REMOTE_FIRESIM_DIR JAVA_OPTS="$REMOTE_JAVA_OPTS" SBT_OPTS="$REMOTE_SBT_OPTS" testOnly ${mapping[$1]}
