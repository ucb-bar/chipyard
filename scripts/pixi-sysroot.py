#!/usr/bin/env python3
"""Lower the compiler sysroot to a published version supported by this host."""
import argparse
import re
import subprocess
from pathlib import Path

# conda-forge does not publish a sysroot for every host glibc release.
SYSROOTS = ("2.39", "2.34", "2.28", "2.17", "2.12")


def version(value):
    return tuple(map(int, value.split(".")))


def adapt_manifest(text, host):
    supported = next((v for v in SYSROOTS if version(v) <= version(host)), None)
    if supported is None:
        raise ValueError("No supported sysroot for glibc " + host)

    def lower(match):
        old = match.group(2)
        return match.group(1) + (supported if version(old) > version(host) else old) + match.group(3)

    text = re.sub(r'(sysroot_linux-(?:64|aarch64)\s*=\s*")([0-9.]+)(\.\*")', lower, text)
    # Pixi solves against the manifest's virtual glibc baseline, not the build
    # sysroot. Lower that baseline too when supporting an older Linux host.
    text = re.sub(r'(glibc\s*=\s*")([0-9.]+)(")', lower, text)
    text = re.sub(r'(family\s*=\s*"glibc",\s*version\s*=\s*")([0-9.]+)(")', lower, text)
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--host-glibc")
    args = parser.parse_args()
    host = args.host_glibc
    if host is None:
        host = subprocess.check_output(["getconf", "GNU_LIBC_VERSION"], text=True).split()[-1]
    if not re.fullmatch(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?", host):
        parser.error("Cannot detect host glibc: " + host)
    original = args.manifest.read_text()
    updated = adapt_manifest(original, host)
    if original != updated:
        args.manifest.write_text(updated)
        print("Adjusted Pixi sysroot / virtual glibc for host " + host)
    else:
        print("Pixi sysroot is compatible with host glibc " + host)


if __name__ == "__main__":
    main()
