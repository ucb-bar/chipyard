#!/bin/bash

set -e

echoerr() { echo "$@" 1>&2; }

CURRENT_DIR="${PWD##*/}"
if [[ "$CURRENT_DIR" != "vcs" ]]; then
    echoerr "Error: This script must be run from chipyard/sims/vcs."
    exit 1
fi

source ./scripts/env.sh > /dev/null

rm -f /tmp/markers.log
runtime() {
    log_path="output/chipyard.harness.TestHarness.$1/kernel.radiance.gemm.$2.log"
    check_exists "${log_path}"
    if [ -z "$(tail -n10 ${log_path} | rg 'finish called')" ]; then
        echo "$3,0"
        return
    fi
    rg "(e0d0a013|be90a013)" ${log_path} > /tmp/markers.log
    echo -n "$3,"
    python3 ./scripts/runtime_fast.py /tmp/markers.log
    rm -f /tmp/markers.log
}

check_exists() {
    if ! [ -f "$1" ]; then
        echoerr "Error: looked for file $1 that does not exist."
        exit 1
    fi
}

echo ",cycles"
dims=(256 512 1024)
for dim in "${dims[@]}"; do
    runtime VirgoFP16Config tcore.volta.dim${dim}    "volta${dim}"
    runtime VirgoFP16Config tcore.ampere.dim${dim}   "ampere${dim}"
    runtime VirgoHopperConfig tcore.hopper.dim${dim} "hopper${dim}"
    runtime VirgoHopperConfig virgo.hopper.dim${dim} "virgo${dim}"
done
