#!/usr/bin/env bash

set -ex

CUR_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
CY_DIR=$(cd "$CUR_DIR/.." && pwd)

REQS_DIR="$CY_DIR/conda-reqs"
CONDA_LOCK_ENV_PATH="$CY_DIR/.conda-lock-env"

if [[ ! -d "$REQS_DIR" ]]; then
  echo "$REQS_DIR does not exist"
  exit 1
fi

CONDA_EXE="${CONDA_EXE:-$(command -v conda || true)}"
if [[ -z "$CONDA_EXE" || ! -x "$CONDA_EXE" ]]; then
  echo "No conda executable found; initialize Miniforge or set CONDA_EXE."
  exit 1
fi

# isolated conda-lock environment if build-setup hasn't ran yet
if [[ ! -x "$CONDA_LOCK_ENV_PATH/bin/conda-lock" ]]; then
  echo "Creating conda-lock environment at $CONDA_LOCK_ENV_PATH"
  "$CONDA_EXE" create -y -p "$CONDA_LOCK_ENV_PATH" -c conda-forge conda-lock
fi

# We never authenticate to a private index, so disable the keyring to avoid weird errs
export PYTHON_KEYRING_BACKEND=keyring.backends.null.Keyring

trap 'rm -f "${TMP_LOCKFILE:-}"' EXIT

for TOOLCHAIN_TYPE in riscv-tools; do
  for PLATFORM in linux-64 linux-aarch64; do
    LOCKFILE="$REQS_DIR/conda-lock-reqs/conda-requirements-$TOOLCHAIN_TYPE-$PLATFORM.conda-lock.yml"
    # Keep the temporary lock beside the destination so relative source paths
    # remain valid after installation. conda-lock requires a nonexistent file.
    TMP_LOCKFILE=$(mktemp "$LOCKFILE.XXXXXX.conda-lock.yml")
    rm -f "$TMP_LOCKFILE"

    "$CONDA_LOCK_ENV_PATH/bin/conda-lock" \
      --conda "$CONDA_EXE" \
      -f "$REQS_DIR/chipyard-base.yaml" \
      -f "$REQS_DIR/chipyard-extended.yaml" \
      -f "$REQS_DIR/docs.yaml" \
      -f "$REQS_DIR/$TOOLCHAIN_TYPE.yaml" \
      -p "$PLATFORM" \
      --lockfile "$TMP_LOCKFILE"
    mv -f "$TMP_LOCKFILE" "$LOCKFILE"

    LOCKFILE="$REQS_DIR/conda-lock-reqs/conda-requirements-$TOOLCHAIN_TYPE-$PLATFORM-lean.conda-lock.yml"
    TMP_LOCKFILE=$(mktemp "$LOCKFILE.XXXXXX.conda-lock.yml")
    rm -f "$TMP_LOCKFILE"

    "$CONDA_LOCK_ENV_PATH/bin/conda-lock" \
      --conda "$CONDA_EXE" \
      -f "$REQS_DIR/chipyard-base.yaml" \
      -f "$REQS_DIR/docs.yaml" \
      -f "$REQS_DIR/$TOOLCHAIN_TYPE.yaml" \
      -p "$PLATFORM" \
      --lockfile "$TMP_LOCKFILE"
    mv -f "$TMP_LOCKFILE" "$LOCKFILE"
  done
done
