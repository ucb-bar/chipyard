#!/bin/bash

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# call clean on exit
trap clean EXIT

cd $LOCAL_CHIPYARD_DIR
./scripts/init-submodules-no-riscv-tools.sh
cd sims/firesim/sim/midas

# set stricthostkeychecking to no (must happen before rsync)
run "echo \"Ping $SERVER\""

clean

# copy over riscv-tools, and chipyard to remote
run "mkdir -p $REMOTE_CHIPYARD_DIR"
run "mkdir -p $REMOTE_RISCV_DIR"

copy $LOCAL_CHIPYARD_DIR/ $SERVER:$REMOTE_CHIPYARD_DIR
copy $LOCAL_RISCV_DIR/ $SERVER:$REMOTE_RISCV_DIR

# Copy ivy2 and sbt directories

run "cp -r ~/.ivy2 $REMOTE_WORK_DIR"
run "cp -r ~/.sbt  $REMOTE_WORK_DIR"

TOOLS_DIR=$REMOTE_RISCV_DIR
LD_LIB_DIR=$REMOTE_RISCV_DIR/lib

# Run midasexamples test

run "export FIRESIM_ENV_SOURCED=1; make -C $REMOTE_FIRESIM_DIR clean"
run "export RISCV=\"$TOOLS_DIR\"; export LD_LIBRARY_PATH=\"$LD_LIB_DIR\"; \
     export FIRESIM_ENV_SOURCED=1; \
     make -C $REMOTE_FIRESIM_DIR JAVA_ARGS=\"$REMOTE_JAVA_ARGS\" TARGET_PROJECT=midasexamples test"
