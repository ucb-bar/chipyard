#!/usr/bin/env bash
# Compatibility alias for the former Conda lockfile generator.
set -euo pipefail
CY_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
exec pixi lock --manifest-path "$CY_DIR/pixi-reqs/pixi.toml" "$@"
