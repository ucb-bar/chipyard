#!/usr/bin/env python3

import argparse
import json
import os
import subprocess
import sys


class TestCommandFailed:
    def __init__(self, label, command, returncode):
        self.label = label
        self.command = command
        self.returncode = returncode


def keep_going_for_test_suites(command):
    if command.startswith("make run-asm-tests-fast") or command.startswith("make run-bmark-tests-fast"):
        return command.replace("make ", "make -k ", 1)
    return command


def run_shell(command, chipyard_dir, sim_dir, env, label=None):
    command = keep_going_for_test_suites(command)
    label = label or command
    wrapped = (
        f'source "{chipyard_dir}/env.sh"\n'
        f'cd "{sim_dir}"\n'
        f'{command}'
    )
    print("::group::" + label, flush=True)
    print(f"Running: {command}", flush=True)
    try:
        process = subprocess.Popen(
            ["bash", "-leo", "pipefail", "-c", wrapped],
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        assert process.stdout is not None
        for line in process.stdout:
            print(line, end="", flush=True)
        returncode = process.wait()
        if returncode != 0:
            print(f"\nFAILED: {label}", flush=True)
            print(f"Command: {command}", flush=True)
            print(f"Exit code: {returncode}", flush=True)
            print(
                f"::error title=Generator test command failed::{label} failed with exit code {returncode}",
                flush=True,
            )
            return TestCommandFailed(label, command, returncode)
        return None
    finally:
        print("::endgroup::", flush=True)


def run_entry(entry, chipyard_dir, sim_dir, env):
    failures = []

    if entry.get("custom") is True:
        command = entry.get("string")
        if not command:
            raise ValueError("custom test entry must include a non-empty string")
        failure = run_shell(command, chipyard_dir, sim_dir, env, label=f"custom command: {command}")
        return [failure] if failure else []

    if entry.get("custom") is not False:
        raise ValueError("test entry must set custom to true or false")

    configs = entry.get("config")
    binaries = entry.get("binary")
    loadmem = entry.get("loadmem")

    if not isinstance(configs, list) or not configs:
        raise ValueError("non-custom test entry must include a non-empty config list")
    if not isinstance(binaries, list) or not binaries:
        raise ValueError("non-custom test entry must include a non-empty binary list")
    if loadmem != "1":
        raise ValueError("non-custom test entries must set loadmem to string value \"1\"")

    for config in configs:
        for binary in binaries:
            label = f"CONFIG={config} BINARY={binary} LOADMEM=1"
            failure = run_shell(
                f"make run-binary CONFIG={config} BINARY={binary} LOADMEM=1",
                chipyard_dir,
                sim_dir,
                env,
                label=label,
            )
            if failure:
                failures.append(failure)

    return failures


def main():
    parser = argparse.ArgumentParser(description="Run a JSON-defined Chipyard generator test group.")
    parser.add_argument("test_name")
    parser.add_argument(
        "--tests-json",
        default="/home/ubuntu/chipyard/.github/workflows/config/generator-tests.json",
    )
    parser.add_argument("--chipyard-dir", default="/home/ubuntu/chipyard")
    args = parser.parse_args()

    chipyard_dir = os.path.abspath(args.chipyard_dir)
    sim_dir = os.path.join(chipyard_dir, "sims", "verilator")

    with open(args.tests_json, encoding="utf-8") as tests_file:
        tests = json.load(tests_file)

    if args.test_name not in tests:
        print(f"Unknown test group: {args.test_name}", file=sys.stderr)
        return 1

    env = os.environ.copy()
    env.setdefault("FORCE_NON_EC2", "1")
    env.setdefault("JVM_OPTS", "-Xmx3200m")
    env["LOCAL_CHIPYARD_DIR"] = chipyard_dir
    env["LOCAL_SIM_DIR"] = sim_dir

    entries = tests[args.test_name]
    if not isinstance(entries, list) or not entries:
        print(f"Test group {args.test_name} must be a non-empty list", file=sys.stderr)
        return 1

    failures = []
    for entry in entries:
        failures.extend(run_entry(entry, chipyard_dir, sim_dir, env))

    if failures:
        print("\n::group::Generator test failure summary", flush=True)
        print(f"Generator test failed: {args.test_name}", flush=True)
        print(f"Failed command count: {len(failures)}", flush=True)
        for index, failure in enumerate(failures, start=1):
            print("", flush=True)
            print(f"{index}. {failure.label}", flush=True)
            print(f"   exit code: {failure.returncode}", flush=True)
            print(f"   command: {failure.command}", flush=True)
        print("::endgroup::", flush=True)
        return 1

    print(f"Generator test complete: {args.test_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
