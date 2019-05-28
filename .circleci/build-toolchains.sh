#!/bin/bash

# create the riscv tools/esp tools binaries
# passed in as <riscv-tools or esp-tools>

# turn echo on and error on earliest command
set -ex

if [ ! -d "$HOME/$1-install" ]; then

    cd $HOME/

    # init all submodules including the tools
    REBAR_DIR=$HOME/project ./project/scripts/build-toolchains.sh $1
fi
