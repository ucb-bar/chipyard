#!/bin/bash

# install verilator

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# clean older directories (delete prior directories related to this branch also)
$SCRIPT_DIR/clean-old-files.sh $CI_DIR
rm -rf $REMOTE_PREFIX*

git clone http://git.veripool.org/git/verilator $REMOTE_VERILATOR_DIR
cd $REMOTE_VERILATOR_DIR
git checkout $VERILATOR_VERSION
autoconf
export VERILATOR_ROOT=$REMOTE_VERILATOR_DIR
./configure
make -j$REMOTE_MAKE_NPROC
