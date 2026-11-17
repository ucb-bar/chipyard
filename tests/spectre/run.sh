#!/usr/bin/env bash
# Build and run one Spectre PoC on a Verilator BOOM config.
#
#   ./run.sh [CONFIG] [v1|v2|rsb]        default: SmallBoomV3Config v1
#   DRAMSIM=1 ./run.sh ...               DRAMsim memory model (slower)
#   TIMEOUT=600 MAX_CYCLES=... ./run.sh
#   ATTACK_BYTES=4 ./run.sh ...          short build (xsim bring-up only)
#
# Test hooks: SIM=<simulator> and BUILD=0 skip the build (test-runner.sh).
#
# Exit status:
#   0  NO_LEAK       valid run, nothing recovered above chance
#   1  LEAK or PARTIAL_LEAK: leakage observed (never a mitigation pass)
#   2  ERROR         bad/timed-out simulation, malformed signature, no result

set -u

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
config=${1:-SmallBoomV3Config}
attack=${2:-v1}
sim=${SIM:-$root/sims/verilator/simulator-chipyard.harness-$config}
bin=${BIN:-$here/$attack.riscv}
# A byte counts as recovered only if it is the top timing hit in at least this
# many probe rounds; a single hit is within noise.
min_hits=${MIN_HITS:-2}

error() { # reason [extra key=value ...]
  echo "SPECTRE_RESULT attack=$attack config=$config result=ERROR reason=$1" \
       "${@:2}"
  [ -n "${log:-}" ] && echo "log      : $log"
  exit 2
}

if [ ! -x "$sim" ]; then
  echo "missing $sim" >&2
  echo "build it: ./ustt make -C sims/verilator CONFIG=$config -j16" >&2
  error no_simulator
fi

if [ "${BUILD:-1}" -eq 1 ]; then
  if ! (cd "$root" && ./ustt make -s -B -C tests/spectre "$attack.riscv"); then
    error build_failed
  fi
fi
[ -x "$bin" ] || error no_binary

dram_flags=""
if [ -n "${DRAMSIM:-}" ]; then
  dram_flags="+dramsim +dramsim_ini_dir=$root/generators/testchipip/src/main/resources/dramsim2_ini"
fi

output_dir=${LOG_DIR:-$root/logs/spectre}
mkdir -p "$output_dir"
sig=$(mktemp "$output_dir/spectre-$attack-$config.XXXXXX.sig")
log=$(mktemp "$output_dir/spectre-$attack-$config.XXXXXX.log")

# Real Verilator runs need the repository environment. A SIM override is a
# test hook and is invoked directly so test-runner.sh stays toolchain-free.
sim_cmd=("$sim")
if [ -z "${SIM:-}" ]; then
  sim_cmd=("$root/ustt" "$sim")
fi

# shellcheck disable=SC2086
timeout "${TIMEOUT:-1800}" "${sim_cmd[@]}" +permissive +verbose \
  +max-cycles="${MAX_CYCLES:-400000000}" +signature="$sig" $dram_flags \
  +permissive-off "$bin" >"$log" 2>&1
simrc=$?

# A signature only counts if the simulator itself finished normally.
[ "$simrc" -eq 124 ] && error timeout simrc="$simrc"
[ "$simrc" -ne 0 ] && error sim_failed simrc="$simrc"

simcycles=$(grep -o 'Completed after *[0-9]* simulation cycles' "$log" |
            grep -o '[0-9]*' || true)
[ -n "$simcycles" ] || error no_completion

if ! decoded=$(python3 - "$sig" "$attack" "$config" "$simcycles" "$min_hits" <<'PY'
import struct
import sys

MAGIC = 0x53504543
EXPECTED = b'!"#ThisIsTheBabyBoomerTest'

sig, attack, config, simcycles, min_hits_text = sys.argv[1:6]


def emit(result, reason="", **fields):
    extra = "".join(" %s=%s" % (k, v) for k, v in fields.items())
    why = " reason=%s" % reason if reason else ""
    print("RESULT: %s" % result)
    print("SPECTRE_RESULT attack=%s config=%s result=%s%s%s"
          % (attack, config, result, why, extra))
    sys.exit(0)


try:
    min_hits = int(min_hits_text)
except ValueError:
    emit("ERROR", "bad_min_hits", value=min_hits_text)

try:
    text = open(sig).read()
    data = b"".join(bytes.fromhex(tok)[::-1] for tok in text.split())
except OSError:
    emit("ERROR", "no_signature")
except ValueError:
    emit("ERROR", "malformed_signature")

if len(data) < 128:
    emit("ERROR", "short_signature", bytes=len(data))

magic, mask, cycles = struct.unpack_from("<IIQ", data, 0)
if magic != MAGIC:
    emit("ERROR", "bad_magic", magic="%#x" % magic)

recovered, alt = data[16:42], data[42:68]
count0, count1 = data[68:94], data[94:120]
attempted, rounds = data[120], data[121]

if not 1 <= attempted <= len(EXPECTED):
    emit("ERROR", "bad_attempted", attempted=attempted)
if not 1 <= rounds <= 255:
    emit("ERROR", "bad_rounds", rounds=rounds)
if not 2 <= min_hits <= rounds:
    emit("INCONCLUSIVE", "confidence_unavailable",
         attempted=attempted, rounds=rounds, min_hits=min_hits)
if max(max(count0), max(count1)) > rounds:
    emit("ERROR", "bad_counts", rounds=rounds)

computed_mask = sum(1 << i for i in range(len(EXPECTED))
                    if recovered[i] == EXPECTED[i])
if mask != computed_mask:
    emit("ERROR", "mask_mismatch", mask="%#x" % mask,
         computed="%#x" % computed_mask)

matches = [i for i in range(attempted) if recovered[i] == EXPECTED[i]]
confident = [i for i in matches if count0[i] >= min_hits]

if attempted == len(EXPECTED) and len(confident) == attempted:
    result = "LEAK"
elif confident:
    result = "PARTIAL_LEAK"
elif attempted < len(EXPECTED):
    result = "INCONCLUSIVE"
else:
    result = "NO_LEAK"

print("recovered: %r" % recovered[:attempted])
print("expected : %r" % EXPECTED[:attempted])
print("alt      : %r" % alt[:attempted])
print("count0   : %s" % list(count0[:attempted]))
print("count1   : %s" % list(count1[:attempted]))
print("cycles   : %d (rdcycle)" % cycles)
print("mask     : %#010x (%d/%d bytes, %d above chance)"
      % (mask, len(matches), attempted, len(confident)))
emit(result,
     attempted=attempted,
     rounds=rounds,
     recovered=len(confident),
     matches=len(matches),
     confidence_min=min((count0[i] for i in confident), default=0),
     cycles=cycles,
     simcycles=simcycles)
PY
); then
  error decoder_crash
fi

printf '%s\n' "$decoded"
verdict=$(printf '%s\n' "$decoded" |
          sed -n 's/^SPECTRE_RESULT .* result=\([A-Z_]*\).*$/\1/p' | tail -1)

echo "sim      : $config $attack (exit $simrc, $simcycles cycles)"
echo "wall     : ${SECONDS}s"
echo "log      : $log"

case "$verdict" in
  LEAK|PARTIAL_LEAK) exit 1 ;;
  NO_LEAK) exit 0 ;;
  ERROR|INCONCLUSIVE) exit 2 ;;
  *) error no_verdict ;;
esac
