#!/usr/bin/env bash

# Run type checking on CI Python files

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
CY_DIR=$SCRIPT_DIR/..

mypy --no-incremental \
    $CY_DIR/.github/scripts
