#!/bin/bash

# create the different verilator builds
# argument is the make command string

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

cd $REMOTE_CHIPYARD_DIR
./scripts/init-submodules-no-riscv-tools.sh --force

# Run Firesim Scala Tests
export FIRESIM_ENV_SOURCED=1;
export COURSIER_CACHE=$REMOTE_WORK_DIR/.coursier-cache
JAVA_TOOL_OPTIONS="$REMOTE_JAVA_OPTS -Djava.io.tmpdir=$(mktemp -d -t cy-fsim-XXXXXXXX)"
make -C $REMOTE_FIRESIM_DIR JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS" SBT_OPTS="$REMOTE_SBT_OPTS" TARGET_SBT_PROJECT="{file:$REMOTE_CHIPYARD_DIR}firechip" testOnly ${mapping[$1]}
