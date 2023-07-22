#!/bin/bash

if [ $(basename "$PWD") != "vcs" ]
then
  echo "Run inside sims/vcs. Exiting."
  exit 1
fi

set -ex

configurations=("MemtraceCoreNV64B2IdConfig" "MemtraceCoreNV128B2IdConfig" "MemtraceCoreNV256B2IdConfig" "MemtraceCoreNV64B8IdConfig" "MemtraceCoreNV128B8IdConfig" "MemtraceCoreNV256B8IdConfig" "MemtraceCoreNV64B16IdConfig" "MemtraceCoreNV128B16IdConfig" "MemtraceCoreNV256B16IdConfig" "MemtraceCoreNV64B32IdConfig" "MemtraceCoreNV128B32IdConfig" "MemtraceCoreNV256B32IdConfig") 
# Disabled as Chipyard fails to elaborate with 512b sbus:
# "MemtraceCoreNV512B8IdConfig" "MemtraceCoreNV512B16IdConfig" "MemtraceCoreNV512B32IdConfig"

rm -f coal_perf.txt
# make clean

for config in "${configurations[@]}"; do
    make CONFIG="$config" run-binary BINARY=none
    logfile="output/chipyard.harness.TestHarness.$config/none.log"
    time=$(grep "simulation time" "$logfile" | awk '{print $NF}')
    echo "($config, $time)" >> coal_perf.txt
done

cat coal_perf.txt
