#!/bin/bash

# build verilator

# turn echo on and error on earliest command
set -ex

cd $HOME/project

cd sims/verisim

if [ ! -d "$HOME/project/sims/verisim/verilator" ]; then
    # make verilator
    make verilator_install
fi
