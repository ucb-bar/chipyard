#!/usr/bin/env python3
"""Turn Pixi's evaluated activation metadata into reversible shell assignments."""
import json
import os
import re
import shlex
import sys


def assignment(name, value):
    if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", name):
        raise ValueError("Invalid environment variable: " + name)
    return "unset " + name if value is None else "export " + name + "=" + shlex.quote(value)


def main():
    variables = json.load(sys.stdin)["environment_variables"]
    root = sys.argv[1]
    # These paths belong to the active environment, including packages added later.
    riscv = variables.get("RISCV")
    if riscv:
        for name in ("C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH"):
            previous = variables.get(name, os.environ.get(name, ""))
            variables[name] = ":".join(filter(None, (riscv + "/include", root + "/tools/DRAMSim2", previous)))
    if os.path.isfile(root + "/software/firemarshal/marshal"):
        variables["PATH"] = root + "/software/firemarshal:" + variables["PATH"]
    restore = "\n".join(assignment(name, os.environ.get(name)) for name in sorted(variables))
    print("_CY_PIXI_RESTORE=" + shlex.quote(restore))
    for name, value in sorted(variables.items()):
        print(assignment(name, value))


if __name__ == "__main__":
    main()
