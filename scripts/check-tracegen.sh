#!/bin/bash

set -e

SCRIPT_DIR=$(dirname $0)
AXE_DIR=$(realpath ${SCRIPT_DIR}/../tools/axe)
ROCKET_DIR=$(realpath ${SCRIPT_DIR}/../generators/rocket-chip)

TO_AXE=${ROCKET_DIR}/scripts/toaxe.py
TO_AXE_PY3=/tmp/toaxe.py
AXE=${AXE_DIR}/src/axe
AXE_SHRINK=${AXE_DIR}/src/axe-shrink.py
AXE_SHRINK_PY3=/tmp/axe-shrink.py

# TODO: convert scripts to py3 in src
2to3 $TO_AXE -o /tmp -n -w
sed -i '30d' $TO_AXE_PY3 # remove import sets
2to3 $AXE_SHRINK -o /tmp -n -w

PATH=$PATH:${AXE_DIR}/src

grep '.*:.*#.*@' $1 > /tmp/clean-trace.txt
python "$TO_AXE_PY3" /tmp/clean-trace.txt > /tmp/trace.axe
result=$("$AXE" check wmo /tmp/trace.axe)

if [ "$result" != OK ]; then
    "$AXE_SHRINK_PY3" wmo /tmp/trace.axe
else
    echo OK
fi
