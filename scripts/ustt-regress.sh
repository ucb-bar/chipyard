#!/usr/bin/env bash
# Regression for one BOOM config: build, riscv-tests, Spectre attacks, Spike cosim, switches-off Verilog diff. Local only (Verilator builds are too heavy for CI). Run one config at a time: concurrent runs fight over the sbt lock.
#
#   scripts/ustt-regress.sh SmallBoomV3Config
#   scripts/ustt-regress.sh MediumBoomV3STTConfig
#   scripts/ustt-regress.sh --selftest      # check the verdict/exit/config-name logic only
#
# Env: JOBS                                            make -j (default 16)
#      T_BUILD T_ASM T_BMARK T_ATTACK T_COSIM T_DIFF   per-stage timeout, s
#      BASELINE_REF  baseline-v0 reference for the switches-off diff (default logs/ref, recorded once with scripts/rtl/switches-off.sh --save-ref; see scripts/rtl/README.md)
#
# Exit: 0 all PASS (or N/A), 1 any FAIL (timeout included), 3 nothing failed but a stage was SKIPped. A skip is never a pass.
#
# A protected config (STT, STTAgeMatrix, STTImpede, USTT, Conservative) must NOT leak and its twin is the plain <X>Config. Any other config is a baseline: attacks MUST leak (else the PoC is broken) and it is its own twin.

set -u

root=$(cd "$(dirname "$0")/.." && pwd)

# Results go to a file, not arrays, so stages can run in the background.
add() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >>"$logdir/summary.tsv"; }

# stage <name> <timeout> <expect-attack-verdict|-> <cmd...>
stage() {
  local name=$1 to=$2 want=$3; shift 3
  local log="$logdir/$name.log" t0=$SECONDS rc st note=""
  timeout "$to" "$@" >"$log" 2>&1; rc=$?
  if [ "$rc" -eq 124 ]; then st=FAIL note="timeout ${to}s"
  elif [ "$want" != - ]; then
    local got; got=$(sed -n 's/^SPECTRE_RESULT .* result=\([A-Z_]*\).*/\1/p' "$log" | tail -1)
    note="got ${got:-none}, want $want"
    [ "$got" = "$want" ] && st=PASS || st=FAIL
  elif [ "$rc" -eq 0 ]; then st=PASS
  else st=FAIL note="exit $rc"
  fi
  add "$name" "$st" "$((SECONDS - t0))" "$note${note:+, }log $log"
}

# classify CONFIG -> "<want-attack-verdict> <twin>"
classify() {
  local twin
  twin=$(printf '%s\n' "$1" | sed -E 's/(USTT|STTAgeMatrix|STTImpede|STT|Conservative)Config$/Config/')
  if [ "$twin" != "$1" ]; then echo "NO_LEAK $twin"; else echo "LEAK $twin"; fi
}

status_of() { awk -F'\t' -v n="$1" '$1 == n { print $2 }' "$logdir/summary.tsv"; }

summary() { # prints table, returns the exit code
  printf '\n%-18s %-5s %7s  %s\n' STAGE RESULT WALL_S NOTE
  awk -F'\t' '{ printf "%-18s %-5s %7s  %s\n", $1, $2, $3, $4 }' "$logdir/summary.tsv"
  cut -f2 "$logdir/summary.tsv" | grep -qx FAIL && return 1
  cut -f2 "$logdir/summary.tsv" | grep -qx SKIP && return 3
  return 0
}

if [ "${1:-}" = --selftest ]; then
  logdir=$(mktemp -d); trap 'rm -rf "$logdir"' EXIT
  for kv in SmallBoomV3Config=LEAK:SmallBoomV3Config \
            MediumBoomV3STTConfig=NO_LEAK:MediumBoomV3Config \
            SmallBoomV3STTAgeMatrixConfig=NO_LEAK:SmallBoomV3Config \
            MediumBoomV3STTImpedeConfig=NO_LEAK:MediumBoomV3Config \
            MediumBoomV3USTTConfig=NO_LEAK:MediumBoomV3Config \
            MediumBoomV3ConservativeConfig=NO_LEAK:MediumBoomV3Config; do
    got=$(classify "${kv%=*}"); want=${kv#*=}; want=${want/:/ }
    [ "$got" = "$want" ] || { echo "selftest: classify ${kv%=*} is '$got', want '$want'"; exit 1; }
  done
  stage ok 5 - true
  stage bad 5 - false
  stage slow 1 - sleep 5
  printf 'SPECTRE_RESULT attack=v1 config=X result=LEAK\n' >"$logdir/fake"
  stage leak 5 LEAK cat "$logdir/fake"
  stage noleak 5 NO_LEAK cat "$logdir/fake"
  stage bg1 5 - sleep 1 & stage bg2 5 - false & wait
  summary >"$logdir/out"; rc=$?
  for kv in ok=PASS bad=FAIL slow=FAIL leak=PASS noleak=FAIL bg1=PASS bg2=FAIL; do
    got=$(status_of "${kv%=*}")
    [ "$got" = "${kv#*=}" ] || { echo "selftest: ${kv%=*} is '$got', want ${kv#*=}"; exit 1; }
  done
  [ "$rc" -eq 1 ] || { echo "selftest: exit $rc, want 1"; exit 1; }
  grep -q 'timeout 1s' "$logdir/out" || { echo "selftest: timeout not reported"; exit 1; }
  : >"$logdir/summary.tsv"; add a PASS 0 x; add b SKIP 0 x
  summary >/dev/null; rc=$?
  [ "$rc" -eq 3 ] || { echo "selftest: skip-only exit $rc, want 3"; exit 1; }
  echo "selftest ok"; exit 0
fi

config=${1:?usage: ustt-regress.sh <CONFIG> | --selftest}
[ "${USTT_ROOT:-}" = "$root" ] || exec "$root/ustt" "$0" "$@"
commit=$(git -C "$root" rev-parse --short HEAD)
[ -n "$(git -C "$root" status --porcelain --untracked-files=no)" ] && commit="$commit (dirty)"
logdir=$root/logs/regress/$config-$(date +%Y%m%d-%H%M%S)
mkdir -p "$logdir"
simdir=$root/sims/verilator
jobs=${JOBS:-16}

read -r want_attack twin < <(classify "$config")

stage build "${T_BUILD:-5400}" - make -C "$simdir" CONFIG="$config" -j"$jobs"
if [ "$(status_of build)" != PASS ]; then
  for s in riscv-asm riscv-bmark attack-v1 attack-v2 attack-rsb cosim; do add "$s" FAIL 0 "build failed"; done
else
  stage riscv-asm "${T_ASM:-3600}" - make -C "$simdir" CONFIG="$config" -j"$jobs" run-asm-tests-fast
  stage riscv-bmark "${T_BMARK:-3600}" - make -C "$simdir" CONFIG="$config" -j"$jobs" run-bmark-tests-fast
  # Each attack is one single-threaded Verilator process; run them together.
  for a in v1 v2 rsb; do
    stage "attack-$a" "$(( ${T_ATTACK:-5400} + 600 ))" "$want_attack" \
      env TIMEOUT="${T_ATTACK:-5400}" "$root/tests/spectre/run.sh" "$config" "$a" &
  done
  wait
fi

# Cosim variant: <X>Config -> <X>CosimConfig. PLAN section 6 runs cosim on Medium only; elsewhere a missing variant is a gap, not N/A.
cosim=${config%Config}CosimConfig
if [ "$(status_of build)" != PASS ]; then
  :
elif grep -rqw "class $cosim" "$root/generators/chipyard/src/main/scala/config/"; then
  stage cosim "${T_COSIM:-7200}" - make -C "$simdir" CONFIG="$cosim" -j"$jobs" run-asm-tests-fast
elif [[ $config == Small* ]]; then
  add cosim N/A 0 "cosim is Medium-only"
else
  add cosim SKIP 0 "no $cosim defined"
fi

# Switches off: the plain twin built from this tree must match baseline-v0. Runs even after a build failure, since it is a different config.
ref=${BASELINE_REF:-$root/logs/ref}/chipyard.harness.TestHarness.$twin
if [ ! -d "$ref/gen-collateral" ]; then
  add verilog-diff SKIP 0 "no $ref; record it with scripts/rtl/switches-off.sh --save-ref $twin"
else
  stage verilog-diff "${T_DIFF:-3600}" - sh -c 'make -C "$1" CONFIG="$2" verilog && "$3/scripts/rtl/verilog-diff.sh" "$4" "$1/generated-src/chipyard.harness.TestHarness.$2"' \
    - "$simdir" "$twin" "$root" "$ref"
fi

echo "config $config, start commit $commit, logs $logdir"
summary; rc=$?
echo "total wall: ${SECONDS}s"
exit "$rc"
