#!/usr/bin/env bash

# This ungodly script surreptitiously builds an archive from existing fesvr objects
# Invoke from riscv-fesvr/build

if [ "x$RISCV" = "x" ]
then
  echo "Please set the RISCV environment variable to your preferred install path."
  exit 1
fi

set -e

objs=$(head -n 1 <(make -f <( echo -e 'include Makefile\n$(info $(value fesvr_objs))') -n))
ar rcs -o libfesvr.a $objs
cp -f libfesvr.a "${RISCV}/lib"

