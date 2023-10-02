#!/usr/bin/env bash

set -ex

CUR_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

REQS_DIR="$CUR_DIR/../conda-reqs"
if [ ! -d "$REQS_DIR" ]; then
  echo "$REQS_DIR does not exist, make sure you're calling this script from chipyard/"
  exit 1
fi

for TOOLCHAIN_TYPE in riscv-tools esp-tools; do
    # note: lock file must end in .conda-lock.yml - see https://github.com/conda-incubator/conda-lock/issues/154
    LOCKFILE=$REQS_DIR/conda-lock-reqs/conda-requirements-$TOOLCHAIN_TYPE-linux-64.conda-lock.yml

    conda-lock -f "$REQS_DIR/chipyard.yaml" -f "$REQS_DIR/$TOOLCHAIN_TYPE.yaml" -p linux-64 --lockfile $LOCKFILE
done
