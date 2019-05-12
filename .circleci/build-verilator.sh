#!/bin/bash

# build verilator and init submodules with rocket-chip hash given by riscv-boom

# turn echo on and error on earliest command
set -ex

cd $HOME/project

# init all submodules (according to what boom-template wants)
./scripts/init-submodules-no-riscv-tools.sh

cd sims/verisim

if [ ! -d "$HOME/project/sims/verisim/verilator" ]; then
    # make boom-template verilator version
    make verilator_install
fi
