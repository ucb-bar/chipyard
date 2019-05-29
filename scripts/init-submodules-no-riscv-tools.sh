#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

unamestr=$(uname)
RDIR=$(pwd)
scripts_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# ignore riscv-tools for submodule init recursive
# you must do this globally (otherwise riscv-tools deep
# in the submodule tree will get pulled anyway
git config --global submodule.riscv-tools.update none
git config --global submodule.esp-tools.update none
git config --global submodule.experimental-blocks.update none
# Disable updates to the FireSim submodule until explicitly requested
git config submodule.sims/firesim.update none
git submodule update --init --recursive #--jobs 8
# unignore riscv-tools,catapult-shell2 globally
git config --global --unset submodule.riscv-tools.update
git config --global --unset submodule.experimental-blocks.update

# Renable firesim and init only the required submodules to provide
# all required scala deps, without doing a full build-setup
git config --unset submodule.sims/firesim.update
cd $scripts_dir/../sims/
git submodule update --init firesim
cd firesim/sim
git submodule update --init midas
cd $RDIR
git config submodule.sims/firesim.update none
