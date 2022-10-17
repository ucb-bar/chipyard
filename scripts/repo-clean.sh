#!/usr/bin/env bash

set -e

# this should be run from chipyard repo top
RDIR=$(git rev-parse --show-toplevel)

rm -rf $RDIR/toolchains/libgloss/build.log
rm -rf $RDIR/toolchains/riscv-tools/riscv-isa-sim/build.log
rm -rf $RDIR/toolchains/riscv-tools/riscv-pk/build.log
rm -rf $RDIR/toolchains/riscv-tools/riscv-tests/build.log
rm -rf $RDIR/toolchains/esp-tools/riscv-isa-sim/build.log
rm -rf $RDIR/toolchains/esp-tools/riscv-pk/build.log
rm -rf $RDIR/toolchains/esp-tools/riscv-tests/build.log
(
    pushd $RDIR/generators/constellation
    if [ -d espresso ]
    then
	git submodule deinit -f espresso
    fi
    popd
)
(
    pushd $RDIR/tools/api-config-chipsalliance
    git config --local status.showUntrackedFiles no
    popd
)
(
    pushd $RDIR/generators/cva6/src/main/resources/vsrc
    if [ -d cva6 ]
    then
	git submodule deinit -f cva6
    fi
    popd
)
