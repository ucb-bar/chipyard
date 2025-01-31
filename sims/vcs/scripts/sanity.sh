#!/bin/bash

set -e

echoerr() { echo "$@" 1>&2; }

CURRENT_DIR="${PWD##*/}"
if [[ "$CURRENT_DIR" != "vcs" ]]; then
    echoerr "Error: This script must be run from chipyard/sims/vcs."
    exit 1
fi

source ./scripts/env.sh > /dev/null

check_exists() {
    if ! [ -f "$1" ]; then
        echo "Error: looked for file $1 that does not exist."
        exit 1
    fi
}

echo "Checking if all kernels have been compiled.."

dims=(256 512 1024)
for dim in "${dims[@]}"; do
    check_exists "$KERNELS_PATH/sgemm_tcore/kernel.radiance.gemm.tcore.volta.dim${dim}.elf"
    check_exists "$KERNELS_PATH/sgemm_tcore/kernel.radiance.gemm.tcore.ampere.dim${dim}.elf"
    check_exists "$KERNELS_PATH/sgemm_tcore/kernel.radiance.gemm.tcore.hopper.dim${dim}.elf"
    check_exists "$KERNELS_PATH/sgemm_gemmini_dma/kernel.radiance.gemm.virgo.hopper.dim${dim}.elf"
done

check_exists "$KERNELS_PATH/flash_attention/kernel.radiance.flash.ampere.seqlen1024.headdim64.elf"
check_exists "$KERNELS_PATH/flash_attention/kernel.radiance.flash.virgo.seqlen1024.headdim64.elf"

echo "Checking if all simulation binaries have been compiled.."

check_exists "simv-chipyard.harness-VirgoFP16Config"
check_exists "simv-chipyard.harness-VirgoFP16Config-debug"
check_exists "simv-chipyard.harness-VirgoHopperConfig"
check_exists "simv-chipyard.harness-VirgoHopperConfig-debug"
check_exists "simv-chipyard.harness-VirgoFlashConfig"

echo "Sanity check passed!"
