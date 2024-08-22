#!/usr/bin/env bash

trap "exit" INT
set -e
set -o pipefail

firesim launchrunfarm  -c $1
firesim infrasetup -c $1
firesim runworkload  -c $1
