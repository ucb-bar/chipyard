#!/usr/bin/env bash
# exit script if any command fails 
set -e
set -o pipefail

# Initialize HAMMER and CAD-plugins
git submodule update --init --recursive vlsi/hammer
git submodule update --init --recursive vlsi/hammer-cad-plugins

# Initialize HAMMER tech plugin
git submodule update --init --recursive vlsi/hammer-"$1"-plugin
