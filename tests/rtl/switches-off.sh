#!/bin/bash
# Switches-off check (AGENTS.md section 4): with every mitigation switch off, a
# config's generated Verilog must match the frozen baseline-v0 output.
#
# Usage:
#   tests/rtl/switches-off.sh [--ref DIR] CONFIG...
#   tests/rtl/switches-off.sh [--ref DIR] --save-ref CONFIG...
#
# --save-ref records the reference: run it on a checkout whose generators match
# baseline-v0 (git diff --stat baseline-v0 -- generators/ is empty).
# The reference defaults to logs/ref/ (git-ignored; about 100-400 MB per config).
# Elaboration logs go to logs/switches-off/. Exit status is non-zero if any
# config fails to elaborate or differs.

set -uo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
ref=$root/logs/ref
save=0
configs=()
while [ $# -gt 0 ]; do
  case $1 in
    --ref) ref=$2; shift 2 ;;
    --save-ref) save=1; shift ;;
    *) configs+=("$1"); shift ;;
  esac
done
if [ ${#configs[@]} -eq 0 ]; then
  echo "usage: $0 [--ref DIR] [--save-ref] CONFIG..." >&2
  exit 2
fi

logs=$root/logs/switches-off
mkdir -p "$logs" "$ref"
cd "$root"
status=0
for c in "${configs[@]}"; do
  gen=sims/verilator/generated-src/chipyard.harness.TestHarness.$c
  if ! ./ustt make -C sims/verilator CONFIG="$c" verilog > "$logs/$c.log" 2>&1; then
    echo "$c: elaboration FAILED (log: $logs/$c.log)"
    status=1
    continue
  fi
  if [ $save -eq 1 ]; then
    rm -rf "$ref/chipyard.harness.TestHarness.$c"
    cp -a "$gen" "$ref/"
    echo "$c: reference saved at commit $(git rev-parse --short HEAD) to $ref"
  else
    result=$("$root/tests/rtl/verilog-diff.sh" "$ref/chipyard.harness.TestHarness.$c" "$gen")
    rc=$?
    echo "$c: $(echo "$result" | tail -1)"
    if [ $rc -ne 0 ]; then
      echo "$result" | sed '$d'
      status=1
    fi
  fi
done
exit $status
