#!/bin/bash

# create the riscv tools/esp tools binaries

# turn echo on and error on earliest command
set -ex

if [ ! -d "$HOME/esp-tools-install" ]; then

    cd $HOME/

    # init all submodules including the tools
    REBAR_DIR=$HOME/project ./project/scripts/build-toolchains.sh esp-tools
fi

if [ ! -d "$HOME/riscv-tools-install" ]; then

    cd $HOME/

    # init all submodules including the tools
    REBAR_DIR=$HOME/project ./project/scripts/build-toolchains.sh riscv-tools
fi
