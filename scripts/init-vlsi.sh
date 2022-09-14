#!/usr/bin/env bash
# exit script if any command fails
set -e
set -o pipefail

# Initialize HAMMER and CAD-plugins
git submodule update --init --recursive vlsi/hammer
if [[ $1 != *openroad* ]] && [[ $2 != *openroad* ]]; then
    git submodule update --init --recursive vlsi/hammer-cadence-plugins
    git submodule update --init --recursive vlsi/hammer-synopsys-plugins
    git submodule update --init --recursive vlsi/hammer-mentor-plugins
fi

# Initialize HAMMER tech plugin
if [[ $1 != *asap7* ]] && [[ $1 != *sky130* ]]; then
    git submodule update --init --recursive vlsi/hammer-$1-plugin
fi
