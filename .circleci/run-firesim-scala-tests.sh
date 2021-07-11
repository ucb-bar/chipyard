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
cd $LOCAL_CHIPYARD_DIR/sims/firesim/sim/firesim-lib/src/main/cc/lib
git submodule update --init elfutils libdwarf
cd $LOCAL_CHIPYARD_DIR/sims/firesim
./scripts/build-libelf.sh
./scripts/build-libdwarf.sh
cd $LOCAL_CHIPYARD_DIR

make -C $LOCAL_CHIPYARD_DIR/tools/dromajo/dromajo-src/src

# set stricthostkeychecking to no (must happen before rsync)
run "echo \"Ping $SERVER\""

clean

# copy over riscv/esp-tools, and chipyard to remote
run "mkdir -p $REMOTE_CHIPYARD_DIR"
run "mkdir -p $REMOTE_RISCV_DIR"
copy $LOCAL_CHIPYARD_DIR/ $SERVER:$REMOTE_CHIPYARD_DIR
copy $LOCAL_RISCV_DIR/ $SERVER:$REMOTE_RISCV_DIR

run "cp -r ~/.ivy2 $REMOTE_WORK_DIR"
run "cp -r ~/.sbt  $REMOTE_WORK_DIR"

TOOLS_DIR=$REMOTE_RISCV_DIR
LD_LIB_DIR=$REMOTE_RISCV_DIR/lib


# Run Firesim Scala Tests
run "export RISCV=\"$TOOLS_DIR\"; \
     export LD_LIBRARY_PATH=\"$LD_LIB_DIR\"; \
     export FIRESIM_ENV_SOURCED=1; \
     export PATH=\"$REMOTE_VERILATOR_DIR/bin:\$PATH\"; \
     export VERILATOR_ROOT=\"$REMOTE_VERILATOR_DIR\"; \
     export COURSIER_CACHE=\"$REMOTE_WORK_DIR/.coursier-cache\"; \
     make -C $REMOTE_FIRESIM_DIR JAVA_OPTS=\"$REMOTE_JAVA_OPTS\" SBT_OPTS=\"$REMOTE_SBT_OPTS\" testOnly ${mapping[$1]}"
