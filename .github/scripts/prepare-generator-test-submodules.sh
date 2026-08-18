#!/usr/bin/env bash
set -euo pipefail

test_name="${1:?usage: prepare-generator-test-submodules.sh TEST_NAME [CHIPYARD_DIR] [TESTS_JSON]}"
chipyard_dir="${2:-/home/ubuntu/chipyard}"
tests_json="${3:-${chipyard_dir}/.github/workflows/config/generator-tests.json}"

is_empty_dir() {
  local path="$1"
  [ ! -d "${path}" ] || [ -z "$(find "${path}" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]
}

update_submodule_if_needed() {
  local repo_dir="$1"
  local submodule_path="$2"
  local abs_path="${repo_dir}/${submodule_path}"
  local status
  local state

  status="$(git -C "${repo_dir}" submodule status -- "${submodule_path}" 2>/dev/null || true)"
  if [ -z "${status}" ]; then
    echo "No submodule named ${submodule_path} under ${repo_dir}" >&2
    exit 1
  fi

  state="${status:0:1}"
  if [ "${state}" = "-" ] || [ "${state}" = "+" ] || is_empty_dir "${abs_path}"; then
    echo "Initializing/updating BINARY owner submodule: ${submodule_path}"
    git -C "${repo_dir}" submodule sync -- "${submodule_path}"
    git -C "${repo_dir}" submodule update --init -- "${submodule_path}"
  else
    echo "BINARY owner submodule already initialized: ${submodule_path}"
  fi
}

submodule_paths="$(
  git -C "${chipyard_dir}" config --file .gitmodules --get-regexp '^submodule\..*\.path$' 2>/dev/null \
    | awk '{ print $2 }'
)"

owner_submodule_for_path() {
  local path="$1"
  local best=""
  local submodule_path

  while IFS= read -r submodule_path; do
    [ -n "${submodule_path}" ] || continue
    if [ "${path}" = "${submodule_path}" ] || [[ "${path}" == "${submodule_path}/"* ]]; then
      if [ "${#submodule_path}" -gt "${#best}" ]; then
        best="${submodule_path}"
      fi
    fi
  done <<< "${submodule_paths}"

  printf "%s\n" "${best}"
}

binary_paths_for_test() {
  python3 - "${tests_json}" "${test_name}" "${chipyard_dir}" <<'PY'
import json
import os
import re
import shlex
import sys

tests_json, test_name, chipyard_dir = sys.argv[1], sys.argv[2], os.path.abspath(sys.argv[3])
sim_dir = os.path.join(chipyard_dir, "sims", "verilator")

with open(tests_json, encoding="utf-8") as tests_file:
    tests = json.load(tests_file)

entries = tests.get(test_name, [])
env = {
    "LOCAL_CHIPYARD_DIR": chipyard_dir,
    "LOCAL_SIM_DIR": sim_dir,
    "RISCV": os.environ.get("RISCV", "$RISCV"),
}

def expand(value, local_env):
    def replacement(match):
        name = match.group("braced") or match.group("plain")
        return local_env.get(name, os.environ.get(name, match.group(0)))

    return re.sub(
        r"\$(?:\{(?P<braced>[A-Za-z_][A-Za-z0-9_]*)\}|(?P<plain>[A-Za-z_][A-Za-z0-9_]*))",
        replacement,
        value,
    )

def maybe_print_chipyard_relative(path):
    if path in ("", "none"):
        return

    path = os.path.normpath(path)
    if not os.path.isabs(path):
        path = os.path.normpath(os.path.join(sim_dir, path))

    try:
        relpath = os.path.relpath(path, chipyard_dir)
    except ValueError:
        return

    if relpath == "." or relpath.startswith(".."):
        return

    print(relpath)

def collect_command_binaries(command):
    if not isinstance(command, str):
        return

    local_env = dict(env)
    try:
        tokens = shlex.split(command)
    except ValueError as err:
        print(f"Could not parse command for BINARY paths: {err}: {command}", file=sys.stderr)
        return

    for token in tokens:
        if "=" not in token:
            continue
        name, value = token.split("=", 1)
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
            continue
        expanded = expand(value, local_env)
        local_env[name] = expanded
        if name == "BINARY":
            maybe_print_chipyard_relative(expanded)

for entry in entries:
    if not isinstance(entry, dict):
        continue
    for binary in entry.get("binary") or []:
        maybe_print_chipyard_relative(expand(str(binary), env))
    collect_command_binaries(entry.get("string"))
PY
}

declare -A required_roots=()

cd "${chipyard_dir}"
git config --global --add safe.directory "*" 2>/dev/null || true

while IFS= read -r binary_path; do
  [ -n "${binary_path}" ] || continue
  owner="$(owner_submodule_for_path "${binary_path}")"
  if [ -n "${owner}" ]; then
    required_roots["${owner}"]=1
  fi
done < <(binary_paths_for_test | sort -u)

if [ "${#required_roots[@]}" -eq 0 ]; then
  echo "No BINARY paths for ${test_name} require submodule initialization"
  exit 0
fi

for root in "${!required_roots[@]}"; do
  update_submodule_if_needed "${chipyard_dir}" "${root}"
done
