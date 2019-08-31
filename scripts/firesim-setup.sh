#!/usr/bin/env bash

# Sets up FireSim for use as a library within REBAR

set -e
set -o pipefail

RDIR=$(pwd)
scripts_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd "${scripts_dir}/.."

# Reenable the FireSim submodule
git config --unset submodule.sims/firesim.update || true
git submodule update --init sims/firesim
cd sims/firesim
./build-setup.sh "$@" --library
cd "$RDIR"
