#!/usr/bin/env bash
set -euo pipefail

suite="${1:?usage: run-firesim-tests.sh SUITE PR_NUMBER [CHIPYARD_DIR] [CONFIG_JSON]}"
pr_number="${2:?usage: run-firesim-tests.sh SUITE PR_NUMBER [CHIPYARD_DIR] [CONFIG_JSON]}"
chipyard_dir="${3:-/home/ubuntu/chipyard}"
config_json="${4:-${chipyard_dir}/.github/workflows/config/firesim-tests.json}"

echo "FireSim test suite: ${suite}"
echo "FireSim test timeout: ${FIRESIM_TEST_TIMEOUT_SECONDS:-7200}s"
echo "FireSim heartbeat interval: ${FIRESIM_HEARTBEAT_SECONDS:-60}s"
echo "FireSim Java heap size: ${JAVA_HEAP_SIZE:-FireSim default}"
echo "FireSim Verilator make flags: ${VERILATOR_MAKEFLAGS:-FireSim default}"

source_with_nounset_disabled() {
  local script="$1"
  shift
  local had_nounset=0

  case "$-" in
    *u*)
      had_nounset=1
      set +u
      ;;
  esac

  # Conda activation/deactivation hooks can reference optional backup
  # variables that are unset under bash nounset.
  # shellcheck disable=SC1090
  source "${script}" "$@"

  if [ "${had_nounset}" -eq 1 ]; then
    set -u
  fi
}

prepare_firesim_environment() {
  cd "${chipyard_dir}/sims/firesim"
  source_with_nounset_disabled sourceme-manager.sh --skip-ssh-setup
}

prepare_f2_mgmt_tools() {
  cd "${chipyard_dir}/sims/firesim/platforms/f2/aws-fpga-firesim-f2/sdk/userspace"
  export AWS_FPGA_REPO_DIR
  AWS_FPGA_REPO_DIR="$(git rev-parse --show-toplevel)"
  bash ./mkall_fpga_mgmt_tools.sh
}

run_with_heartbeat() {
  local timeout_seconds="${1:?timeout required}"
  shift
  local heartbeat_seconds="${FIRESIM_HEARTBEAT_SECONDS:-60}"
  local command_display="$*"
  local heartbeat_pid
  local rc
  local had_errexit=0

  (
    local elapsed=0
    while true; do
      sleep "${heartbeat_seconds}"
      elapsed=$((elapsed + heartbeat_seconds))
      printf '[%s] still running after %ss: %s\n' \
        "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
        "${elapsed}" \
        "${command_display}"
    done
  ) &
  heartbeat_pid="$!"

  case "$-" in
    *e*)
      had_errexit=1
      set +e
      ;;
  esac

  timeout --foreground --kill-after=60s "${timeout_seconds}s" "$@"
  rc="$?"

  kill "${heartbeat_pid}" 2>/dev/null || true
  wait "${heartbeat_pid}" 2>/dev/null || true

  if [ "${had_errexit}" -eq 1 ]; then
    set -e
  fi

  if [ "${rc}" -eq 124 ]; then
    echo "Timed out after ${timeout_seconds}s: ${command_display}" >&2
  elif [ "${rc}" -eq 137 ]; then
    echo "Timed out and was force-killed after ${timeout_seconds}s: ${command_display}" >&2
  fi

  return "${rc}"
}

run_firesim_scala_test() {
  local test_class="$1"
  local timeout_seconds="${FIRESIM_TEST_TIMEOUT_SECONDS:-7200}"

  if ! [[ "${test_class}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid FireSim Scala test class name: ${test_class}" >&2
    exit 1
  fi

  if ! grep -Eq "class[[:space:]]+${test_class}([[:space:]({]|$)" \
    "${chipyard_dir}/generators/firechip/chip/src/test/scala/FireSimTestSuite.scala"; then
    echo "FireSim Scala test class does not exist: ${test_class}" >&2
    exit 1
  fi

  cd "${chipyard_dir}"
  echo "========== FireSim Scala test: firechip.chip.${test_class} =========="
  run_with_heartbeat \
    "${timeout_seconds}" \
    env TEST_DISABLE_VCS=1 TEST_DISABLE_VIVADO=1 \
    sbt "project firechip; testOnly firechip.chip.${test_class}"
}

prepare_firesim_environment

if [ "${suite}" = "f2" ]; then
  prepare_f2_mgmt_tools
fi

while IFS= read -r test_class; do
  run_firesim_scala_test "${test_class}"
done < <(
  python3 - "${config_json}" "${suite}" <<'PY'
import json
import sys

config_json, suite = sys.argv[1], sys.argv[2]
with open(config_json, encoding="utf-8") as config_file:
    config = json.load(config_file)

if suite not in config:
    print(f"Unknown FireSim test suite: {suite}", file=sys.stderr)
    sys.exit(1)

tests = config[suite]
if not isinstance(tests, list):
    print(f"FireSim suite {suite} must be a list of Scala test class names", file=sys.stderr)
    sys.exit(1)

for test in tests:
    print(test)
PY
)
