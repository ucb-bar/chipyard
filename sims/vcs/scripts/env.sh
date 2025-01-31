#!/bin/bash

ENV_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export KERNELS_PATH="$(realpath ${ENV_SCRIPT_DIR}/../../../../virgo-kernels/kernels)"
