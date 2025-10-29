#!/bin/bash

#-------------------------------------------------------------
# test the verilator simulation using csmith random testing
#
# run location: circle ci docker image
# usage:
#   $1 - config string
#   $2 - name of the toolchain to build
#-------------------------------------------------------------

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

SIM_BASE=simulator-chipyard-
CONFIG=$(echo ${mapping[$1]} | sed -n -e 's/^.*CONFIG=\([a-zA-Z0-9]*\).*/\1/p')
SIM=${SIM_BASE}${CONFIG}
AMT_RUNS=$2

# run csmith utility
cd $LOCAL_CHECKOUT_DIR/util/csmith
./install-csmith.sh
./run-csmith.sh --sim $LOCAL_SIM_DIR/$SIM --run $AMT_RUNS --nodebug
