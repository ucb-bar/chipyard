#!/bin/bash

set -e

CURRENT_DIR="${PWD##*/}"
if [[ "$CURRENT_DIR" != "vcs" ]]; then
    echo "Error: This script must be run from chipyard/sims/vcs."
    exit 1
fi

source ./scripts/env.sh

echo -e "\nCompiling volta & ampere designs"
make CONFIG=VirgoFP16Config
make CONFIG=VirgoFP16Config debug
echo -e "\nCompiling hopper & virgo designs"
make CONFIG=VirgoHopperConfig
make CONFIG=VirgoHopperConfig debug
echo -e "\nCompilation completed"
