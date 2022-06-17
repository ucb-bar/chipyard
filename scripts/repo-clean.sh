#!/usr/bin/env bash

set -e

# this should be run from chipyard repo top
TOPDIR=$(pwd)

cd generators/cva6/src/main/resources/vsrc
git submodule deinit cva6

cd $TOPDIR

cd toolchains/qemu/roms/
git submodule deinit edk2
cd ../
rm -rf build

cd ../libgloss
rm -rf build.log

cd ../riscv-tools/riscv-isa-sim/
rm -rf build.log

cd ../riscv-pk
rm -rf build.log

cd ../riscv-tests
rm -rf build.log

cd $TOPDIR
cd tools/api-config-chipsalliance
git config --local status.showUntrackedFiles no
