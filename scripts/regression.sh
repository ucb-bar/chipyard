#!/bin/bash

# NOTE: TEMPORARY UNTIL CI IS ONLINE

# Run by just giving the test to run (run-bmark-tests | run-asm-tests)
# Runs in vsim and verisim

set -ex
set -euo pipefail

cd sims/vsim/

make SUB_PROJECT=rocketchip CONFIG=DefaultConfig
make SUB_PROJECT=rocketchip CONFIG=DefaultConfig $1
make SUB_PROJECT=boom CONFIG=BoomConfig
make SUB_PROJECT=boom CONFIG=BoomConfig $1
make SUB_PROJECT=example CONFIG=DefaultRocketConfig
make SUB_PROJECT=example CONFIG=DefaultRocketConfig $1
make SUB_PROJECT=boomexample CONFIG=DefaultBoomConfig
make SUB_PROJECT=boomexample CONFIG=DefaultBoomConfig $1

cd ../verisim/

make SUB_PROJECT=rocketchip CONFIG=DefaultConfig
make SUB_PROJECT=rocketchip CONFIG=DefaultConfig $1
make SUB_PROJECT=boom CONFIG=BoomConfig
make SUB_PROJECT=boom CONFIG=BoomConfig $1
make SUB_PROJECT=example CONFIG=DefaultRocketConfig
make SUB_PROJECT=example CONFIG=DefaultRocketConfig $1
make SUB_PROJECT=boomexample CONFIG=DefaultBoomConfig
make SUB_PROJECT=boomexample CONFIG=DefaultBoomConfig $1
