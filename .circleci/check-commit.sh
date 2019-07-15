#!/bin/bash

# check to see that submodule commits are present on the master branch

# turn echo on and error on earliest command
set -ex

# enter bhd repo
cd $HOME/project

# initialize submodules and get the hashes
git config submodule.vlsi/hammer-cad-plugins.update none
git submodule update --init
status=$(git submodule status)

search () {
    for submodule in "${submodules[@]}"
    do
        echo "Running check on submodule $submodule in $dir"
        hash=$(echo "$status" | grep $submodule | awk '{print$1}' | grep -o "[[:alnum:]]*")
        echo "Searching for $hash in origin/master of $submodule"
        git -C $dir/$submodule branch -r --contains "$hash" | grep "origin/master" # needs init'ed submodules
    done
}

submodules=("boom" "hwacha" "rocket-chip" "sifive-blocks" "testchipip")
dir="generators"

search

submodules=("esp-tools" "riscv-tools")
dir="toolchains"

search

submodules=("barstools" "chisel3" "firrtl" "torture")
dir="tools"

search

echo "Done checking all submodules"
