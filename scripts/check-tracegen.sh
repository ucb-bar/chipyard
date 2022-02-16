#!/bin/bash

set -e

SCRIPT_DIR=$(dirname $0)
AXE_DIR=$(realpath ${SCRIPT_DIR}/../tools/axe)
ROCKET_DIR=$(realpath ${SCRIPT_DIR}/../generators/rocket-chip)

TO_AXE=${ROCKET_DIR}/scripts/toaxe.py
AXE=${AXE_DIR}/src/axe
AXE_SHRINK=${AXE_DIR}/src/axe-shrink.py

PATH=$PATH:${AXE_DIR}/src

grep '.*:.*#.*@' $1 > /tmp/clean-trace.txt
python2 "$TO_AXE" /tmp/clean-trace.txt > /tmp/trace.axe
result=$("$AXE" check wmo /tmp/trace.axe)

if [ "$result" != OK ]; then
    "$AXE_SHRINK" wmo /tmp/trace.axe
else
    echo OK
fi
