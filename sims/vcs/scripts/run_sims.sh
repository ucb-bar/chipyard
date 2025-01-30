#!/bin/bash

set -e

CURRENT_DIR="${PWD##*/}"
if [[ "$CURRENT_DIR" != "vcs" ]]; then
    echo "Error: This script must be run from chipyard/sims/vcs."
    exit 1
fi

if [[ -z "${TMUX}" ]]; then
    echo "Error: you must be in a tmux session to run simulations."
fi

source ./scripts/env.sh

lineno=0

start_run() {
    echo "kickoff elf $KERNELS_PATH/$2 on config $1"
    lineno=$((lineno+1))
    make CONFIG=$1 BINARY="$KERNELS_PATH/$2" LOADMEM=1 run-binary"$4" 2>&1 | ./scripts/pprint "$3" $lineno &
}

check_exists() {
    if ! [ -f "$1" ]; then
        echo "Error: looked for file $1 that does not exist."
        exit 1
    fi
}

check_exists "simv-chipyard.harness-VirgoFP16Config"
check_exists "simv-chipyard.harness-VirgoHopperConfig"
check_exists "simv-chipyard.harness-VirgoFP16Config-debug"
check_exists "simv-chipyard.harness-VirgoHopperConfig-debug"

# sanity check that the kernels have been compiled
check_exists "$KERNELS_PATH/sgemm_tcore/kernel.radiance.gemm.tcore.volta.dim256.elf"
check_exists "$KERNELS_PATH/sgemm_tcore/kernel.radiance.gemm.tcore.hopper.dim512.elf"
check_exists "$KERNELS_PATH/sgemm_gemmini_dma/kernel.radiance.gemm.virgo.hopper.dim1024.elf"

echo "Simulations will be started in parallel in 5 seconds. Please do not Ctrl+C as it kills all subprocesses."

sleep 5

suffix="-debug"

dims=(256 512 1024)
for dim in "${dims[@]}"; do
    echo "$element"
    start_run VirgoFP16Config sgemm_tcore/kernel.radiance.gemm.tcore.volta.dim${dim}.elf          "volta${dim} " "${suffix}"
    start_run VirgoFP16Config sgemm_tcore/kernel.radiance.gemm.tcore.ampere.dim${dim}.elf         "ampere${dim}" "${suffix}"
    start_run VirgoHopperConfig sgemm_tcore/kernel.radiance.gemm.tcore.hopper.dim${dim}.elf       "hopper${dim}" "${suffix}"
    start_run VirgoHopperConfig sgemm_gemmini_dma/kernel.radiance.gemm.virgo.hopper.dim${dim}.elf "virgo${dim} " "${suffix}"
    suffix=""
done

wait

echo "All simulations have finished!"
