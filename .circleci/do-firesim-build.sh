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
cd sims/firesim/sim/midas && git submodule update --init

# set stricthostkeychecking to no (must happen before rsync)
run "echo \"Ping $SERVER\""

clean

# copy over riscv/esp-tools, verilator, and chipyard to remote
run "mkdir -p $REMOTE_CHIPYARD_DIR"
run "mkdir -p $REMOTE_VERILATOR_DIR"
copy $LOCAL_CHIPYARD_DIR/ $SERVER:$REMOTE_CHIPYARD_DIR
copy $LOCAL_VERILATOR_DIR/ $SERVER:$REMOTE_VERILATOR_DIR

TOOLS_DIR=$REMOTE_RISCV_DIR
LD_LIB_DIR=$REMOTE_RISCV_DIR/lib
VERILATOR_BIN_DIR=$REMOTE_VERILATOR_DIR/install/bin

if [ $1 = "hwacha" ]; then
    TOOLS_DIR=$REMOTE_ESP_DIR
    LD_LIB_DIR=$REMOTE_ESP_DIR/lib
    run "mkdir -p $REMOTE_ESP_DIR"
    copy $LOCAL_ESP_DIR/ $SERVER:$REMOTE_ESP_DIR
else
    run "mkdir -p $REMOTE_RISCV_DIR"
    copy $LOCAL_RISCV_DIR/ $SERVER:$REMOTE_RISCV_DIR
fi

# Build MIDAS-level verilator sim
FIRESIM_VARS="${mapping[$1]}"
run "export FIRESIM_ENV_SOURCED=1; make -C $REMOTE_FIRESIM_DIR clean"
run "export RISCV=\"$TOOLS_DIR\"; export LD_LIBRARY_PATH=\"$LD_LIB_DIR\"; \
     export PATH=\"$VERILATOR_BIN_DIR:\$PATH\"; export FIRESIM_ENV_SOURCED=1; \
     export VERILATOR_ROOT=$REMOTE_VERILATOR_DIR/install/share/verilator; \
     make -C $REMOTE_FIRESIM_DIR JAVA_ARGS=\"-Xmx8G -Xss8M\" $FIRESIM_VARS verilator"
run "rm -rf $REMOTE_CHIPYARD_DIR/project"

# copy back the final build
mkdir -p $LOCAL_CHIPYARD_DIR
copy $SERVER:$REMOTE_CHIPYARD_DIR/ $LOCAL_CHIPYARD_DIR

# Fix dramsim2_ini symlink
export $FIRESIM_VARS
ln -sf $LOCAL_FIRESIM_DIR/midas/src/main/resources/dramsim2_ini $LOCAL_FIRESIM_DIR/generated-src/f1/${DESIGN}-${TARGET_CONFIG}-${PLATFORM_CONFIG}/dramsim2_ini
