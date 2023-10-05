#!/usr/bin/env bash

# This script is intended to be used as a sub-step of build-setup.sh.

set -e
pushd $CYDIR/sims/verilator
make launch-sbt SBT_COMMAND=";project chipyard; compile"
make launch-sbt SBT_COMMAND=";project tapeout; compile"
popd

