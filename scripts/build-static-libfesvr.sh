#!/usr/bin/env bash

# This ungodly script surreptitiously builds an archive from existing fesvr objects
# Invoke from riscv-fesvr/build

if [ "x$RISCV" = "x" ]
then
  echo "Please set the RISCV environment variable to your preferred install path."
  exit 1
fi

set -e

objs=$(make -n -f <(
    echo 'include Makefile'
    echo '$(info $(value fesvr_objs))'
    ) | head -n 1)

ar rcs -o libfesvr.a $objs
cp -f libfesvr.a "${RISCV}/lib"

