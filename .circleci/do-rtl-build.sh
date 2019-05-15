#!/bin/bash

# create the different verilator builds
# argument is the make command string

# turn echo on and error on earliest command
set -ex

# init all submodules
cd $HOME/project
./scripts/init-submodules-no-riscv-tools.sh

# enter the verisim directory and build the specific config
cd sims/verisim
make clean

# run the particular build command
make JAVA_ARGS="-Xmx2G -Xss8M" $@

rm -rf ../../project
