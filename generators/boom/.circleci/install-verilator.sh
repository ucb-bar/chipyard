#!/bin/bash

# move verilator to the remote server

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# set stricthostkeychecking to no (must happen before rsync)
run "echo \"Ping $SERVER\""

run "git clone https://git.veripool.org/git/verilator $REMOTE_VERILATOR_DIR; \
     cd $REMOTE_VERILATOR_DIR; \
     git checkout $VERILATOR_VERSION; \
     autoconf; \
     export VERILATOR_ROOT=$REMOTE_VERILATOR_DIR; \
     ./configure; \
      make -j$REMOTE_MAKE_NPROC;"
