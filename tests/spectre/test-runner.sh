#!/usr/bin/env bash
# Verdict-logic self-checks for run.sh: mock simulator, synthetic signatures.
# No RTL simulation and no build.  Run: ./test-runner.sh
set -u

here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d "${TMPDIR:-/tmp}/spectre-test.XXXXXX")
trap 'rm -rf "$tmp"' EXIT

mock=$tmp/mock-sim
cat >"$mock" <<'EOF'
#!/usr/bin/env bash
sig=
for a in "$@"; do
  case $a in +signature=*) sig=${a#+signature=} ;; esac
done
[ -n "${MOCK_SLEEP:-}" ] && sleep "$MOCK_SLEEP"
if [ -n "${MOCK_SIG:-}" ] && [ -n "$sig" ]; then cp "$MOCK_SIG" "$sig"; fi
[ "${MOCK_COMPLETE:-1}" = 1 ] &&
  echo "*** PASSED *** Completed after 4242 simulation cycles"
exit "${MOCK_EXIT:-0}"
EOF
chmod +x "$mock"

# make_sig FILE MASK ATTEMPTED ROUNDS COUNT0 [MAGIC]
make_sig() {
  python3 - "$@" <<'PY'
import struct
import sys

out, mask, attempted, rounds, count0 = sys.argv[1:6]
mask = int(mask, 0)
attempted, rounds, count0 = map(int, (attempted, rounds, count0))
magic = int(sys.argv[6], 0) if len(sys.argv) > 6 else 0x53504543
secret = b'!"#ThisIsTheBabyBoomerTest'
hit = [(mask >> i) & 1 and i < len(secret) for i in range(len(secret))]
rec = bytes(secret[i] if hit[i] else 0 for i in range(len(secret)))
count = bytes(count0 if hit[i] else 0 for i in range(len(secret)))
data = (struct.pack("<IIQ", magic, mask, 123456) + rec + bytes(len(secret))
        + count + bytes(len(secret)) + bytes([attempted, rounds]) + bytes(6))
assert len(data) == 128, len(data)
open(out, "w").write("".join(data[i:i+16][::-1].hex() + "\n"
                             for i in range(0, 128, 16)))
PY
}

make_sig "$tmp/full.sig" 0x3ffffff 26 3 3
make_sig "$tmp/short.sig" 0x1 1 3 3
make_sig "$tmp/short-none.sig" 0 1 3 0
make_sig "$tmp/partial.sig" 0x1ffffff 26 3 3 # 25/26 bytes
make_sig "$tmp/none.sig" 0 26 3 0            # control: no hits
make_sig "$tmp/chance.sig" 0x3ffffff 26 3 1  # one hit per byte
make_sig "$tmp/badmagic.sig" 0x3ffffff 26 3 3 0xdeadbeef
printf 'nothex\n' >"$tmp/malformed.sig"
head -n 4 "$tmp/full.sig" >"$tmp/truncated.sig" # 64 valid bytes

pass=0
fail=0

check() { # NAME EXPECTED_EXIT EXPECTED_RESULT [ENV=VALUE ...]
  local name=$1 want_rc=$2 want=$3 out=$tmp/$1.out line got rc
  shift 3
  env SIM="$mock" BIN="$mock" BUILD=0 LOG_DIR="$tmp" \
    "$@" "$here/run.sh" SmallBoomV3Config v1 \
    >"$out" 2>&1
  rc=$?
  line=$(grep -m1 '^SPECTRE_RESULT ' "$out" || true)
  got=$(printf '%s\n' "$line" | sed -n 's/.* result=\([A-Z_]*\).*/\1/p')
  if [ "$rc" -eq "$want_rc" ] && [ "$got" = "$want" ]; then
    pass=$((pass + 1))
    echo "ok   $name: result=$got exit=$rc"
  else
    fail=$((fail + 1))
    echo "FAIL $name: result=${got:-none} exit=$rc (want $want/$want_rc)"
    sed 's/^/     /' "$out"
  fi
}

check full-leak 1 LEAK MOCK_SIG="$tmp/full.sig"
check short-run 1 PARTIAL_LEAK MOCK_SIG="$tmp/short.sig"
check short-no-evidence 2 INCONCLUSIVE MOCK_SIG="$tmp/short-none.sig"
check partial-25of26 1 PARTIAL_LEAK MOCK_SIG="$tmp/partial.sig"
check control-no-leak 0 NO_LEAK MOCK_SIG="$tmp/none.sig"
check chance-below-threshold 0 NO_LEAK MOCK_SIG="$tmp/chance.sig"
check sim-failed-with-sig 2 ERROR MOCK_SIG="$tmp/full.sig" MOCK_EXIT=7
check sim-failed-no-sig 2 ERROR MOCK_EXIT=7
check sim-timeout 2 ERROR MOCK_SIG="$tmp/full.sig" MOCK_SLEEP=3 TIMEOUT=1
check sim-timeout-no-sig 2 ERROR MOCK_SLEEP=3 TIMEOUT=1
check malformed-hex 2 ERROR MOCK_SIG="$tmp/malformed.sig"
check truncated-signature 2 ERROR MOCK_SIG="$tmp/truncated.sig"
check invalid-magic 2 ERROR MOCK_SIG="$tmp/badmagic.sig"
check no-completion 2 ERROR MOCK_SIG="$tmp/full.sig" MOCK_COMPLETE=0

# The machine line must carry the coverage/confidence contract.
grep -q 'result=LEAK .*attempted=26 rounds=3 recovered=26 ' \
  "$tmp/full-leak.out" ||
  { fail=$((fail + 1)); echo "FAIL full-leak line lacks coverage fields"; }
grep -q 'result=PARTIAL_LEAK .*attempted=1 rounds=3 recovered=1 ' \
  "$tmp/short-run.out" ||
  { fail=$((fail + 1)); echo "FAIL short-run line lacks coverage fields"; }
for output in "$tmp"/*.out; do
  [ "$(grep -c '^SPECTRE_RESULT ' "$output")" -eq 1 ] ||
    { fail=$((fail + 1)); echo "FAIL $(basename "$output"): expected one result line"; }
done

echo
if [ "$fail" -ne 0 ]; then
  echo "$fail check(s) failed"
  exit 1
fi
echo "all $((pass + 2)) checks passed"
