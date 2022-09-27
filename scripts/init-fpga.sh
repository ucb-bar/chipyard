#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

# Enable submodule update for FPGA tools.
git config --unset submodule.fpga/fpga-shells.update || :
# Initialize local FPGA tools.
git submodule update --init --recursive fpga/fpga-shells
# Disable submodule update for FPGA tools.
git config submodule.fpga/fpga-shells.update none
