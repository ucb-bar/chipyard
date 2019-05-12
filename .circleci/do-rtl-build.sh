#!/bin/bash

# create the different verilator builds
# 1st argument is the subproject
# 2nd argument is the config (can be unspecified)

# turn echo on and error on earliest command
set -ex

# init all submodules
cd $HOME/project
./scripts/init-submodules-no-riscv-tools.sh

# enter the verisim directory and build the specific config
cd sims/verisim
make clean

# run the particular build command
if [ $# -ne 0 ]; then
  if [ $# -eq 1 ]; then
    make SUB_PROJECT=$1 JAVA_ARGS="-Xmx2G -Xss8M"
  elif [ $# -eq 2 ]; then
    make SUB_PROJECT=$1 CONFIG=$2 JAVA_ARGS="-Xmx2G -Xss8M"
  else
    exit 1 # wrong amount of args
  fi
else
  exit 1 # need to provide at least the arg
fi

rm -rf ../../project
