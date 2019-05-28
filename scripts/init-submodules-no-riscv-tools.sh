#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

unamestr=$(uname)
RDIR=$(pwd)

# ignore riscv-tools for submodule init recursive
# you must do this globally (otherwise riscv-tools deep
# in the submodule tree will get pulled anyway
git config --global submodule.riscv-tools.update none
git config --global submodule.esp-tools.update none
git config --global submodule.experimental-blocks.update none
git submodule update --init --recursive #--jobs 8
# unignore riscv-tools,catapult-shell2 globally
git config --global --unset submodule.riscv-tools.update
git config --global --unset submodule.experimental-blocks.update
