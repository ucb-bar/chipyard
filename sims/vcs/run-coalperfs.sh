#!/bin/bash

configurations=("MemtraceCoreNV64B8IdConfig" "MemtraceCoreNV128B8IdConfig" "MemtraceCoreNV256B8IdConfig" "MemtraceCoreNV512B8IdConfig" "MemtraceCoreNV64B16IdConfig" "MemtraceCoreNV128B16IdConfig" "MemtraceCoreNV256B16IdConfig" "MemtraceCoreNV512B16IdConfig" "MemtraceCoreNV64B32IdConfig" "MemtraceCoreNV128B32IdConfig" "MemtraceCoreNV256B32IdConfig" "MemtraceCoreNV512B32IdConfig") 

rm -f coal_perf.txt
make clean

for config in "${configurations[@]}"; do
    time=$(make CONFIG="$config" run-binary-debug BINARY=none | grep "simulation time" | awk '{print $NF}')
    echo "($config, $time)" >> coal_perf.txt
done

cat coal_perf.txt