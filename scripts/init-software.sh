#!/usr/bin/env bash
# exit script if any command fails
set -e
set -o pipefail

# Enable submodule update for software submodules
git config --unset submodule.software/nvdla-workload.update || :
git config --unset submodule.software/coremark.update || :
git config --unset submodule.software/spec2017.update || :

# Initialize local software submodules
git submodule update --init --recursive software/nvdla-workload
git submodule update --init --recursive software/coremark
git submodule update --init --recursive software/spec2017
