#!/bin/bash

# check to see that submodule commits are present on the master branch

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# enter bhd repo
cd $LOCAL_CHIPYARD_DIR

# ignore the private vlsi submodules
git config submodule.vlsi/hammer-cadence-plugins.update none
git config submodule.vlsi/hammer-mentor-plugins.update none
git config submodule.vlsi/hammer-synopsys-plugins.update none

# initialize submodules and get the hashes
git submodule update --init
status=$(git submodule status)

all_names=()


search_submodule() {
    echo "Running check on submodule $submodule in $dir"
    hash=$(echo "$status" | grep "$dir.*$submodule " | awk '{print$1}' | grep -o "[[:alnum:]]*")
    for branch in "${branches[@]}"
    do
        echo "Searching for $hash in origin/$branch of $submodule"
        (git -C $dir/$submodule branch -r --contains "$hash" | grep "origin/$branch") && true # needs init'ed submodules
        if [ $? -eq 0 ]
        then
            all_names+=("$dir/$submodule $hash 0")
            return
        fi
    done
    all_names+=("$dir/$submodule $hash 1")
    return
}

search () {
    for submodule in "${submodules[@]}"
    do
        search_submodule
    done
}

submodules=("boom" "hwacha" "icenet" "sha3" "rocket-chip" "sifive-blocks" "sifive-cache" "testchipip")
dir="generators"
if [ "$CIRCLE_BRANCH" == "master" ] || [ "$CIRCLE_BRANCH" == "dev" ]
then
    branches=("master")
else
    branches=("master" "dev")
fi
search

submodules=("riscv-gnu-toolchain" "riscv-isa-sim" "riscv-pk" "riscv-tests")
dir="toolchains/esp-tools"
branches=("master")
search


submodules=("riscv-gnu-toolchain" "riscv-isa-sim" "riscv-pk" "riscv-tests" "riscv-gnu-toolchain-prebuilt")
dir="toolchains/riscv-tools"
branches=("master")
search

# riscv-openocd doesn't use its master branch
submodules=("riscv-openocd")
dir="toolchains/riscv-tools"
branches=("riscv")
search

submodules=("qemu")
dir="toolchains"
branches=("master")
search

submodules=("spec2017" "coremark")
dir="software"
branches=("master")
search

submodules=("axe" "barstools" "chisel3" "firrtl" "torture" "dsptools" "chisel-testers" "treadle" "firrtl-interpreter")
dir="tools"
if [ "$CIRCLE_BRANCH" == "master" ] || [ "$CIRCLE_BRANCH" == "dev" ]
then
    branches=("master")
else
    branches=("master" "dev")
fi
search

submodules=("firesim")
dir="sims"
if [ "$CIRCLE_BRANCH" == "master" ] || [ "$CIRCLE_BRANCH" == "dev" ]
then
    branches=("master")
else
    branches=("master" "dev")
fi
search

submodules=("hammer")
dir="vlsi"
branches=("master")
search


# turn off verbose printing to make this easier to read
set +x

# print all result strings
for str in "${all_names[@]}";
do
    echo "$str"
done

# check if there was a non-zero return code
for str in "${all_names[@]}";
do
    if [ ! 0 = $(echo "$str" | awk '{print$3}') ]; then
        exit 1
    fi
done

echo "Done checking all submodules"

