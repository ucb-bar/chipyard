#!/bin/bash

# create the different verilator builds
# argument is the make command string

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# call clean on exit
trap clean EXIT

cd $LOCAL_CHIPYARD_DIR
./scripts/init-submodules-no-riscv-tools.sh

# set stricthostkeychecking to no (must happen before rsync)
run "echo \"Ping $SERVER\""

clean

# copy over riscv/esp-tools, verilator, and chipyard to remote
run "mkdir -p $REMOTE_CHIPYARD_DIR"
run "mkdir -p $REMOTE_VERILATOR_DIR"
copy $LOCAL_CHIPYARD_DIR/ $SERVER:$REMOTE_CHIPYARD_DIR
copy $LOCAL_VERILATOR_DIR/ $SERVER:$REMOTE_VERILATOR_DIR

run "cp -r ~/.ivy2 $REMOTE_WORK_DIR"
run "cp -r ~/.sbt  $REMOTE_WORK_DIR"

TOOLS_DIR=$REMOTE_RISCV_DIR
LD_LIB_DIR=$REMOTE_RISCV_DIR/lib

if [ $1 = "chipyard-gemmini" ]; then
    export RISCV=$LOCAL_ESP_DIR
    export LD_LIBRARY_PATH=$LOCAL_ESP_DIR/lib
    export PATH=$RISCV/bin:$PATH
    GEMMINI_SOFTWARE_DIR=$LOCAL_SIM_DIR/../../generators/gemmini/software/gemmini-rocc-tests
    cd $LOCAL_SIM_DIR/../../generators/gemmini/software
    git submodule update --init --recursive gemmini-rocc-tests
    cd gemmini-rocc-tests
    ./build.sh
fi

if [ $1 = "chipyard-hwacha" ] || [ $1 = "chipyard-gemmini" ]; then
    TOOLS_DIR=$REMOTE_ESP_DIR
    LD_LIB_DIR=$REMOTE_ESP_DIR/lib
    run "mkdir -p $REMOTE_ESP_DIR"
    copy $LOCAL_ESP_DIR/ $SERVER:$REMOTE_ESP_DIR
else
    run "mkdir -p $REMOTE_RISCV_DIR"
    copy $LOCAL_RISCV_DIR/ $SERVER:$REMOTE_RISCV_DIR
fi

# enter the verilator directory and build the specific config on remote server
run "make -C $REMOTE_SIM_DIR clean"
run "export RISCV=\"$TOOLS_DIR\"; export LD_LIBRARY_PATH=\"$LD_LIB_DIR\"; \
     export VERILATOR_ROOT=$REMOTE_VERILATOR_DIR/install/share/verilator; \
     make -j$NPROC -C $REMOTE_SIM_DIR VERILATOR_INSTALL_DIR=$REMOTE_VERILATOR_DIR JAVA_ARGS=\"$REMOTE_JAVA_ARGS\" ${mapping[$1]}"
run "rm -rf $REMOTE_CHIPYARD_DIR/project"

# copy back the final build
mkdir -p $LOCAL_CHIPYARD_DIR
copy $SERVER:$REMOTE_CHIPYARD_DIR/ $LOCAL_CHIPYARD_DIR
