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

# copy over riscv/esp-tools, and chipyard to remote
run "mkdir -p $REMOTE_CHIPYARD_DIR"
copy $LOCAL_CHIPYARD_DIR/ $SERVER:$REMOTE_CHIPYARD_DIR

run "cp -r ~/.ivy2 $REMOTE_WORK_DIR"
run "cp -r ~/.sbt  $REMOTE_WORK_DIR"

TOOLS_DIR=$REMOTE_RISCV_DIR
LD_LIB_DIR=$REMOTE_RISCV_DIR/lib

run "mkdir -p $REMOTE_RISCV_DIR"
copy $LOCAL_RISCV_DIR/ $SERVER:$REMOTE_RISCV_DIR

# enter the verilator directory and build the specific config on remote server
run "export RISCV=\"$TOOLS_DIR\"; \
     make -C $REMOTE_FPGA_DIR clean;"

read -a keys <<< ${grouping[$1]}

for key in "${keys[@]}"
do
    run "export RISCV=\"$TOOLS_DIR\"; \
         export LD_LIBRARY_PATH=\"$LD_LIB_DIR\"; \
         export PATH=\"$REMOTE_VERILATOR_DIR/bin:\$PATH\"; \
         export COURSIER_CACHE=\"$REMOTE_WORK_DIR/.coursier-cache\"; \
         make -j$REMOTE_MAKE_NPROC -C $REMOTE_FPGA_DIR FIRRTL_LOGLEVEL=info JAVA_ARGS=\"$REMOTE_JAVA_ARGS\" ${mapping[$key]}"
done

run "rm -rf $REMOTE_CHIPYARD_DIR/project"
