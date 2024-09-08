#!/bin/bash

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

cmake $LOCAL_CHIPYARD_DIR/tests/ -S $LOCAL_CHIPYARD_DIR/tests/ -B $LOCAL_CHIPYARD_DIR/tests/build/ -D CMAKE_BUILD_TYPE=Debug
cmake --build $LOCAL_CHIPYARD_DIR/tests/build/ --target clean
cmake --build $LOCAL_CHIPYARD_DIR/tests/build/ --target all
