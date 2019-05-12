#!/bin/bash

# create the riscv tools binaries from riscv-boom/boom-template with rocket-chip hash given by riscv-boom

# turn echo on and error on earliest command
set -ex

if [ ! -d "$HOME/esp-tools-install" ]; then

    cd $HOME/

    # init all submodules including the tools
    ./project/scripts/build-toolchains.sh esp-tools
fi

if [ ! -d "$HOME/riscv-tools-install" ]; then

    cd $HOME/

    # init all submodules including the tools
    ./project/scripts/build-toolchains.sh riscv-tools
fi
