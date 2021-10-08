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

export RISCV="$GITHUB_WORKSPACE/riscv-tools-install"
export LD_LIBRARY_PATH="$RISCV/lib"
export PATH="$RISCV/bin:$PATH"

# Directory locations for handling firesim-local installations of libelf/libdwarf
# This would generally be handled by build-setup.sh/firesim-setup.sh
firesim_sysroot=lib-install
local_firesim_sysroot=$LOCAL_FIRESIM_DIR/$firesim_sysroot
remote_firesim_sysroot=$REMOTE_FIRESIM_DIR/$firesim_sysroot

cd $LOCAL_CHIPYARD_DIR
./scripts/init-submodules-no-riscv-tools.sh
cd $LOCAL_CHIPYARD_DIR/sims/firesim/sim/firesim-lib/src/main/cc/lib
git submodule update --init elfutils libdwarf
cd $LOCAL_CHIPYARD_DIR/sims/firesim
mkdir -p $local_firesim_sysroot
./scripts/build-libelf.sh $local_firesim_sysroot
./scripts/build-libdwarf.sh $local_firesim_sysroot
cd $LOCAL_CHIPYARD_DIR

# replace the workspace dir with a local dir so you can copy around
sed -i -E 's/(workspace=).*(\/tools)/\1$PWD\2/g' .sbtopts

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

LD_LIB_DIR=$remote_firesim_sysroot/lib:$REMOTE_RISCV_DIR/lib

# Run Firesim Scala Tests
run "export RISCV=\"$TOOLS_DIR\"; \
     export LD_LIBRARY_PATH=\"$LD_LIB_DIR\"; \
     export FIRESIM_ENV_SOURCED=1; \
     export PATH=\"$REMOTE_VERILATOR_DIR/bin:\$PATH\"; \
     export VERILATOR_ROOT=\"$REMOTE_VERILATOR_DIR\"; \
     export COURSIER_CACHE=\"$REMOTE_WORK_DIR/.coursier-cache\"; \
     make -C $REMOTE_FIRESIM_DIR JAVA_OPTS=\"$REMOTE_JAVA_OPTS\" SBT_OPTS=\"$REMOTE_SBT_OPTS\" testOnly ${mapping[$1]}"
