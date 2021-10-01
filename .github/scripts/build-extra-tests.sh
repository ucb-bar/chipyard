#!/bin/bash

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

make -C $LOCAL_CHIPYARD_DIR/tests clean
make -C $LOCAL_CHIPYARD_DIR/tests
