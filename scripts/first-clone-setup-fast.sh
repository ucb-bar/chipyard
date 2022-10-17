#!/usr/bin/env bash

set -ex

{

export MAKEFLAGS=-j16

STARTDIR=$(git rev-parse --show-toplevel)

./build-setup.sh riscv-tools -f
source env.sh

./scripts/firesim-setup.sh
cd sims/firesim
source sourceme-f1-manager.sh --skip-ssh-setup

cd sim
unset MAKEFLAGS
make f1
export MAKEFLAGS=-j16

cd $STARTDIR/software/firemarshal
./init-submodules.sh
marshal -v build br-base.json

cd $STARTDIR
./scripts/repo-clean.sh

} 2>&1 | tee first-clone-setup-fast-log
