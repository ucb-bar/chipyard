#!/bin/bash

set -e

SCRIPT_DIR=$(dirname $0)
AXE_DIR=$(realpath ${SCRIPT_DIR}/../tools/axe)
ROCKET_DIR=$(realpath ${SCRIPT_DIR}/../generators/rocket-chip)

TMP_DIR=$(mktemp -d -t tracegen-XXXXXXXX)
TO_AXE=${ROCKET_DIR}/scripts/toaxe.py
TO_AXE_PY3=${TMP_DIR}/toaxe.py
AXE=${AXE_DIR}/src/axe
AXE_SHRINK=${AXE_DIR}/src/axe-shrink.py
AXE_SHRINK_PY3=${TMP_DIR}/axe-shrink.py

# TODO: convert scripts to py3 in src
2to3 $TO_AXE -o ${TMP_DIR} -n -w
sed -i '30d' $TO_AXE_PY3 # remove import sets
2to3 $AXE_SHRINK -o ${TMP_DIR} -n -w

PATH=$PATH:${AXE_DIR}/src

grep '.*:.*#.*@' $1 > ${TMP_DIR}/clean-trace.txt
python "$TO_AXE_PY3" ${TMP_DIR}/clean-trace.txt > ${TMP_DIR}/trace.axe
result=$("$AXE" check wmo ${TMP_DIR}/trace.axe)

if [ "$result" != OK ]; then
    "$AXE_SHRINK_PY3" wmo ${TMP_DIR}/trace.axe
else
    echo OK
fi
