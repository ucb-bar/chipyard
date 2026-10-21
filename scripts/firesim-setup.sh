#!/usr/bin/env bash

# Sets up FireSim for use as a library within Chipyard

set -e
set -o pipefail

CYDIR=$(git rev-parse --show-toplevel)

cd "$CYDIR"

# Use the complete shared environment even when called directly from a fresh
# shell. FireSim's generated env.sh will reactivate this same Pixi environment.
source "$CYDIR/scripts/pixi-env.sh" default

# Reenable the FireSim submodule
git config --unset submodule.sims/firesim.update || true
pushd sims/firesim
./build-setup.sh "$@" --library
popd
