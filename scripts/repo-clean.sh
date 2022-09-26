#!/usr/bin/env bash

set -e

# this should be run from chipyard repo top
RDIR=$(git rev-parse --show-toplevel)

cd $RDIR/libgloss
rm -rf build.log

cd ../riscv-tools/riscv-isa-sim/
rm -rf build.log

cd ../riscv-pk
rm -rf build.log

cd ../riscv-tests
rm -rf build.log

cd $RDIR/tools/api-config-chipsalliance
git config --local status.showUntrackedFiles no
