#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)

_usage() {
  echo "usage: ${0} [--no-firesim]" >&2
  exit 1
}

NO_FIRESIM=false
while getopts 'h-:' opt ; do
  case ${opt} in
  -)
    case ${OPTARG} in
    no-firesim) NO_FIRESIM=true ;;
    *) echo "invalid option: --${OPTARG}" >&2 ; _usage ;;
    esac ;;
  h) _usage ;;
  *) echo "invalid option: -${opt}" >&2 ; _usage ;;
  esac
done
shift $((OPTIND - 1))

# Ignore toolchain submodules
cd "$RDIR"
for name in toolchains/*/*/ ; do
    git config submodule."${name%/}".update none
done
git config submodule.toolchains/qemu.update none

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
git config --unset submodule.toolchains/qemu.update

git config --unset submodule.vlsi/hammer-cadence-plugins.update
git config --unset submodule.vlsi/hammer-synopsys-plugins.update
git config --unset submodule.vlsi/hammer-mentor-plugins.update

if [ $NO_FIRESIM = false ]; then
echo "initializing firesim"
  # Renable firesim and init only the required submodules to provide
  # all required scala deps, without doing a full build-setup
  git config --unset submodule.sims/firesim.update
  git submodule update --init sims/firesim
  git -C sims/firesim submodule update --init sim/midas
  git -C sims/firesim submodule update --init --recursive sw/firesim-software
  git config submodule.sims/firesim.update none
fi
