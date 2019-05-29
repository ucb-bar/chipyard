#!/usr/bin/env bash

# Sets up FireSim for use as a library within REBAR

set -e
set -o pipefail

RDIR=$(pwd)
scripts_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
sims_dir=$scripts_dir/../sims/

# Reenable the FireSim submodule
git config --unset submodule.sims/firesim.update || true
cd $sims_dir
git submodule update --init firesim
cd firesim
./build-setup.sh $@ --library
cd $RDIR
