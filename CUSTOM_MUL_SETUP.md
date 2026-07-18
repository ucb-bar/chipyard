# Custom BOOM Multipliers Setup

This repository contains custom non-blocking folded multipliers integrated into
the BOOM submodule:

- `MULE2N`
- `MULE3N`
- `MULE5N`

The `generators/boom` submodule points to the BOOM fork that contains the RTL
changes required by these instructions.

## Prerequisites

- Linux machine or server
- Git
- Conda available
- Enough disk space for Chipyard build artifacts

## Clone

```bash
git clone --recurse-submodules https://github.com/ykptn/chipyard.git
cd chipyard
```

If the repository was cloned without submodules:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

## Initial setup

Run the standard Chipyard setup:

```bash
./build-setup.sh riscv-tools
source ./env.sh
```

If the conda environment already exists, load conda first and then source the
generated environment file:

```bash
source ~/miniforge3/etc/profile.d/conda.sh
source ./env.sh
```

## Build BOOM Verilator simulator

The custom multipliers were validated with `SmallBoomV3Config`.

```bash
cd sims/verilator
make SUB_PROJECT=chipyard CONFIG=SmallBoomV3Config firrtl -j8
make SUB_PROJECT=chipyard CONFIG=SmallBoomV3Config run-firtool -j8
make SUB_PROJECT=chipyard CONFIG=SmallBoomV3Config -j8
```

## Build custom multiplier tests

```bash
cd ../../tests
rm -rf build
cmake -S ./ -B ./build -D CMAKE_BUILD_TYPE=Debug
cmake --build ./build --target mule2n
cmake --build ./build --target mule3n
cmake --build ./build --target mule5n
```

## Run tests

```bash
cd ../sims/verilator
make SUB_PROJECT=chipyard CONFIG=SmallBoomV3Config BINARY=../../tests/build/mule2n.riscv run-binary
make SUB_PROJECT=chipyard CONFIG=SmallBoomV3Config BINARY=../../tests/build/mule3n.riscv run-binary
make SUB_PROJECT=chipyard CONFIG=SmallBoomV3Config BINARY=../../tests/build/mule5n.riscv run-binary
```

Expected pass strings:

- `MULE2N PASS (14 cases)`
- `MULE3N PASS (14 cases)`
- `MULE5N PASS (14 cases)`

## Notes

- The server build used for validation is not the source of truth. The source
  of truth is this repository plus the BOOM submodule commit.
- The top-level `chipyard` repository and the `generators/boom` submodule must
  both be committed and pushed.
- If only the top-level repo is pushed without the BOOM fork commit, a fresh
  clone will not reproduce the design.
