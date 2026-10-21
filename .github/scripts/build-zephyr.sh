#!/usr/bin/env bash
set -euo pipefail

chipyard_dir="${LOCAL_CHIPYARD_DIR:-/home/ubuntu/chipyard}"
zephyr_dir="${chipyard_dir}/software/zephyrproject/zephyr"

if [ -z "${RISCV:-}" ]; then
  echo "RISCV is unset. Source env.sh before building Zephyr." >&2
  exit 1
fi

git -C "${chipyard_dir}" submodule update --init -- software/zephyrproject/zephyr

cd "${zephyr_dir}"
if [ ! -d .west ]; then
  west init -l .
fi
west config manifest.file west-riscv.yml
west update

export ZEPHYR_BASE="${zephyr_dir}"
export ZEPHYR_TOOLCHAIN_VARIANT=cross-compile
export CROSS_COMPILE="${RISCV}/bin/riscv64-unknown-elf-"

west build -p -b chipyard_riscv64 samples/chipyard/hello_world/
