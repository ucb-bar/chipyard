#!/bin/bash

# build verilator

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# call clean on exit
trap clean EXIT

if [ ! -d "$LOCAL_VERILATOR_DIR" ]; then
    # set stricthostkeychecking to no (must happen before rsync)
    run "echo \"Ping $SERVER\""

    clean

    run "mkdir -p $REMOTE_CHIPYARD_DIR"
    copy $LOCAL_CHIPYARD_DIR/ $SERVER:$REMOTE_CHIPYARD_DIR

    run "make -C $REMOTE_CHIPYARD_DIR/sims/verisim VERILATOR_INSTALL_DIR=$REMOTE_VERILATOR_DIR verilator_install"

    # copy so that circleci can cache
    mkdir -p $LOCAL_CHIPYARD_DIR
    mkdir -p $LOCAL_VERILATOR_DIR
    copy $SERVER:$REMOTE_CHIPYARD_DIR/  $LOCAL_CHIPYARD_DIR
    copy $SERVER:$REMOTE_VERILATOR_DIR/ $LOCAL_VERILATOR_DIR
fi
