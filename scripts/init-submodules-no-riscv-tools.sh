#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)

NO_FIRESIM=false

while test $# -gt 0
do
  case "$1" in
    --no-firesim)
      NO_FIRESIM=true;
      ;;
  esac
  shift
done

# Ignore toolchain submodules
cd "$RDIR"
for name in toolchains/*/*/ ; do
    git config submodule."${name%/}".update none
done
# Disable updates to the FireSim submodule until explicitly requested
git config submodule.sims/firesim.update none
# Disable updates to the hammer tool plugins repos
git config submodule.vlsi/hammer-cadence-plugins.update none
git config submodule.vlsi/hammer-synopsys-plugins.update none
git config submodule.vlsi/hammer-mentor-plugins.update none
git submodule update --init --recursive #--jobs 8
# Un-ignore toolchain submodules
for name in toolchains/*/*/ ; do
    git config --unset submodule."${name%/}".update
done
git config --unset submodule.vlsi/hammer-cadence-plugins.update
git config --unset submodule.vlsi/hammer-synopsys-plugins.update
git config --unset submodule.vlsi/hammer-mentor-plugins.update

if [ $NO_FIRESIM = false ]; then
  # Renable firesim and init only the required submodules to provide
  # all required scala deps, without doing a full build-setup
  git config --unset submodule.sims/firesim.update
  git submodule update --init sims/firesim
  git -C sims/firesim submodule update --init sim/midas
  git -C sims/firesim submodule update --init --recursive sw/firesim-software
  git config submodule.sims/firesim.update none
fi
