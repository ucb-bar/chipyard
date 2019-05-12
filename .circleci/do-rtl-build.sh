#!/bin/bash

# create the different verilator builds of BOOM based on arg

# turn echo on and error on earliest command
set -ex

# this file assumes cache is updated correctly

# enter the verisim directory and build the specific config
cd $HOME/project/sims/verisim
make clean
make SUB_PROJECT=$1 JAVA_ARGS="-Xmx2G -Xss8M"
