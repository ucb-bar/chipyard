#!/bin/bash
# Compare the generated Verilog of two Chipyard generated-src config directories,
# ignoring source locators (// @[file.scala:line]) and the file:line text in
# assertion messages, which shift whenever a Scala file is edited.
#
# Usage: tests/rtl/verilog-diff.sh REF_DIR NEW_DIR
#   e.g. REF_DIR = logs/ref/chipyard.harness.TestHarness.SmallBoomV3Config
# Exit status: 0 if every .sv/.v file matches, 1 otherwise.

set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 REF_DIR NEW_DIR" >&2
  exit 2
fi

ref=$1/gen-collateral
new=$2/gen-collateral
for d in "$ref" "$new"; do
  [ -d "$d" ] || { echo "missing $d" >&2; exit 2; }
done

strip() {
  sed -E 's#at [a-zA-Z0-9_-]+\.scala:[0-9]+#at X.scala:N#g; s#[[:space:]]*// @\[[^]]*\]##g' "$1"
}

ref_files=$(cd "$ref" && ls -1 *.sv *.v 2>/dev/null | sort)
new_files=$(cd "$new" && ls -1 *.sv *.v 2>/dev/null | sort)
status=0
if [ "$ref_files" != "$new_files" ]; then
  echo "file lists differ:"
  diff <(echo "$ref_files") <(echo "$new_files") || true
  status=1
fi

compared=0
differing=0
for f in $ref_files; do
  [ -f "$new/$f" ] || continue
  compared=$((compared + 1))
  if ! cmp -s <(strip "$ref/$f") <(strip "$new/$f"); then
    differing=$((differing + 1))
    echo "differs: $f"
    status=1
  fi
done

echo "compared=$compared differing=$differing"
exit $status
