#!/bin/bash

# get the hash of riscv-tools

# turn echo on and error on earliest command
set -ex

# enter bhd repo
cd $HOME/project

# get the version of riscv-tools from the git submodule hash
git submodule status | grep "riscv-tools" | awk '{print$1}' | grep -o "[[:alnum:]]*" >> $HOME/riscv-tools.hash
git submodule status | grep "esp-tools" | awk '{print$1}' | grep -o "[[:alnum:]]*" >> $HOME/esp-tools.hash

echo "Hashfile for riscv-tools and esp-tools created in $HOME"
echo "Contents: riscv-tools:$(cat $HOME/riscv-tools.hash)"
echo "Contents: esp-tools:$(cat $HOME/esp-tools.hash)"
