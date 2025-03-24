#!/bin/bash

# create the different verilator builds
# usage:
#   do-rtl-build.sh <make command string> sim
#     run rtl build for simulations and copy back results
#   do-rtl-build.sh <make command string> fpga
#     run rtl build for fpga and don't copy back results

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

cd $REMOTE_CHIPYARD_DIR
git submodule sync
./scripts/init-submodules-no-riscv-tools.sh --full

# Constellation can run without espresso, but this improves
# elaboration time drastically
pushd $REMOTE_CHIPYARD_DIR/generators/constellation
scripts/install-espresso.sh $RISCV
popd

if [ $1 = "group-accels" ]; then
    pushd $REMOTE_CHIPYARD_DIR/generators/gemmini/software
    git submodule update --init --recursive gemmini-rocc-tests
    pushd gemmini-rocc-tests
    ./build.sh
    popd
    popd
fi

# choose what make dir to use
case $2 in
    "sim")
        REMOTE_MAKE_DIR=$REMOTE_SIM_DIR
        ;;
    "fpga")
        REMOTE_MAKE_DIR=$REMOTE_FPGA_DIR
        ;;
esac

# enter the verilator directory and build the specific config on remote server
make -C $REMOTE_MAKE_DIR clean

read -a keys <<< ${grouping[$1]}

# need to set the PATH to use the new verilator (with the new verilator root)
for key in "${keys[@]}"
do
    export COURSIER_CACHE=$REMOTE_COURSIER_CACHE
    export JVM_MEMORY=10G
    export JAVA_TMP_DIR=$REMOTE_JAVA_TMP_DIR
    make -j$REMOTE_MAKE_NPROC -C $REMOTE_MAKE_DIR ${mapping[$key]}
done
