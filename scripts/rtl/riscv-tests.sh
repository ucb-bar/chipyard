#!/bin/bash
# Build the Verilator simulator for each config and run the fast riscv-tests ISA suite and benchmarks. A config fails if the build fails, make exits non-zero (a failed or timed-out test), or any test run did not finish.
#
# Usage: scripts/rtl/riscv-tests.sh [-j N] CONFIG...
# Logs go to logs/riscv-tests/<CONFIG>/; a one-line-per-step summary is appended to logs/riscv-tests/progress.log, which can be followed with tail -f.

set -uo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
jobs=32
configs=()
while [ $# -gt 0 ]; do
  case $1 in
    -j) jobs=$2; shift 2 ;;
    *) configs+=("$1"); shift ;;
  esac
done
if [ ${#configs[@]} -eq 0 ]; then
  echo "usage: $0 [-j N] CONFIG..." >&2
  exit 2
fi

cd "$root"
progress=logs/riscv-tests/progress.log
mkdir -p logs/riscv-tests
log() { echo "$(date +%T) $*" | tee -a "$progress"; }

status=0
log "start commit=$(git rev-parse --short HEAD) configs=${configs[*]}"
for c in "${configs[@]}"; do
  dir=logs/riscv-tests/$c
  mkdir -p "$dir"
  if ! ./ustt make -C sims/verilator CONFIG="$c" -j"$jobs" > "$dir/build.log" 2>&1; then
    log "$c: BUILD FAILED (log: $dir/build.log)"
    status=1
    continue
  fi
  log "$c: build ok"
  for t in run-asm-tests-fast run-bmark-tests-fast; do
    ./ustt make -C sims/verilator CONFIG="$c" -j$((jobs / 2 > 0 ? jobs / 2 : 1)) "$t" > "$dir/$t.log" 2>&1
    rc=$?
    # Every test runs under pipefail and ends with $finish when it completes.
    runs=$(grep -c 'set -o pipefail' "$dir/$t.log")
    finished=$(grep -c 'Verilog \$finish' "$dir/$t.log")
    if [ $rc -eq 0 ] && [ "$runs" -gt 0 ] && [ "$runs" -eq "$finished" ]; then
      log "$c: $t PASS ($finished/$runs)"
    else
      log "$c: $t FAIL (make exit $rc, $finished/$runs finished; log: $dir/$t.log)"
      status=1
    fi
  done
done
log "done status=$status"
exit $status
