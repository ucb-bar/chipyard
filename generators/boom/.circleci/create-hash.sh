#!/bin/bash

#-------------------------------------------------------------
# get the hash of riscv-tools
#
# run location: circle ci docker image
#-------------------------------------------------------------

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# enter bhd repo
cd $HOME

git clone --progress --verbose https://github.com/ucb-bar/chipyard.git chipyard
cd $LOCAL_CHIPYARD_DIR

echo "Checking out Chipyard version: $(cat $LOCAL_CHECKOUT_DIR/CHIPYARD.hash)"
git fetch
git checkout $(cat $LOCAL_CHECKOUT_DIR/CHIPYARD.hash)

# Use normalized output of git-submodule status as hashfile
for tools in 'riscv-tools' 'esp-tools' ; do
    git submodule status "toolchains/${tools}" "toolchains/qemu" | while read -r line ; do
        echo "${line#[!0-9a-f]}"
    done > "${HOME}/${tools}.hash"
done

echo "Hashfile for riscv-tools and esp-tools created in $HOME"
echo "Contents: riscv-tools:$(cat $HOME/riscv-tools.hash)"
echo "Contents: esp-tools:$(cat $HOME/esp-tools.hash)"

rm -rf $LOCAL_CHIPYARD_DIR
