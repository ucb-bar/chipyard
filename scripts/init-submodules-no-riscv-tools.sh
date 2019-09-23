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
git config submodule.toolchains/riscv-tools.update none
git config submodule.toolchains/esp-tools.update none
# Disable updates to the FireSim submodule until explicitly requested
git config submodule.sims/firesim.update none
# Disable updates to the hammer tool plugins repos
git config submodule.vlsi/hammer-cadence-plugins.update none
git config submodule.vlsi/hammer-synopsys-plugins.update none
git config submodule.vlsi/hammer-mentor-plugins.update none
git submodule update --init --recursive #--jobs 8
# unignore riscv-tools,catapult-shell2 globally
git config --unset submodule.toolchains/riscv-tools.update
git config --unset submodule.toolchains/esp-tools.update
git config --unset submodule.vlsi/hammer-cadence-plugins.update
git config --unset submodule.vlsi/hammer-synopsys-plugins.update
git config --unset submodule.vlsi/hammer-mentor-plugins.update

# Renable firesim and init only the required submodules to provide
# all required scala deps, without doing a full build-setup
git config --unset submodule.sims/firesim.update
cd "${scripts_dir}/../sims"
git submodule update --init firesim
cd firesim/sim
git submodule update --init midas
cd "$RDIR"
git config submodule.sims/firesim.update none
