#!/bin/bash

set -e

SCRIPT_DIR=$(dirname $0)
AXE_DIR=$(realpath ${SCRIPT_DIR}/../tools/axe)
ROCKET_DIR=$(realpath ${SCRIPT_DIR}/../generators/rocket-chip)

TMP_DIR=$(mktemp -d -t tracegen-XXXXXXXX)
trap 'rm -rf "$TMP_DIR"' EXIT
TO_AXE=${ROCKET_DIR}/scripts/toaxe.py
TO_AXE_PY3=${TMP_DIR}/toaxe.py
AXE=${AXE_DIR}/src/axe
AXE_SHRINK=${AXE_DIR}/src/axe-shrink.py
AXE_SHRINK_PY3=${TMP_DIR}/axe-shrink.py

run_2to3() {
    local log_file="${TMP_DIR}/2to3.log"
    if command -v 2to3 > /dev/null 2>&1; then
        cmd=(2to3)
    else
        cmd=(python3 -m lib2to3)
    fi

    if ! "${cmd[@]}" "$@" > "$log_file" 2>&1; then
        cat "$log_file" >&2
        exit 1
    fi
}

# TODO: convert scripts to py3 in src
run_2to3 $TO_AXE -o ${TMP_DIR} -n -w
sed -i '30d' $TO_AXE_PY3 # remove import sets
run_2to3 $AXE_SHRINK -o ${TMP_DIR} -n -w

PATH=$PATH:${AXE_DIR}/src

if ! grep '.*:.*#.*@' "$1" > ${TMP_DIR}/clean-trace.txt; then
    echo "ERROR: no tracegen events found in $1" >&2
    echo "The simulator may not have run successfully. First lines of trace output:" >&2
    sed -n '1,40p' "$1" >&2 || true
    exit 1
fi

python "$TO_AXE_PY3" ${TMP_DIR}/clean-trace.txt > ${TMP_DIR}/trace.axe
result=$("$AXE" check wmo ${TMP_DIR}/trace.axe)

if [ "$result" != OK ]; then
    "$AXE_SHRINK_PY3" wmo ${TMP_DIR}/trace.axe
else
    echo OK
fi
