#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

# $CHIPYARD_DIR should be defined and pointing to the top-level folder in Chipyard
if [[ -z "${CHIPYARD_DIR}" ]]; then
  echo "Environment variable \$CHIPYARD_DIR is undefined. Unable to run script."
  exit 1
fi

cd $CHIPYARD_DIR

# speed up setup
export MAKEFLAGS="-j32"

# run setup
./build-setup.sh -f -v

# use chipyard env
source env.sh

# build first set of verilog
cd sims/verilator
make verilog

# build and make verilator binary for verilog built
make

# see verilog
ls -alh generated-src

# see verilator binary
ls -alh simulator*
