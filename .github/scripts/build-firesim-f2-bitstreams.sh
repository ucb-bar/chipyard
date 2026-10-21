#!/usr/bin/env bash
set -euo pipefail

# Run this directly on an existing FireSim manager. The manager's normal AWS
# credential provider chain is used, including an attached EC2 IAM role; this
# script does not create or copy access keys.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_CY_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

CY_DIR="${CY_DIR:-${DEFAULT_CY_DIR}}"
FS_DIR="${FIRESIM_DIR:-${CY_DIR}/sims/firesim}"
BUILD_RECIPES="${FIRESIM_BUILD_RECIPES:-${CY_DIR}/sims/firesim-staging/sample_config_build_recipes.yaml}"
BUILD_CONFIG="${FIRESIM_BUILD_CONFIG:-${SCRIPT_DIR}/config_build_f2.yaml}"
BUILD_HWDB="${FIRESIM_BUILD_HWDB:-${CY_DIR}/sims/firesim-staging/sample_config_hwdb.yaml}"
PUBLISH_HWDB="${FIRESIM_PUBLISH_HWDB:-${FS_DIR}/deploy/built-hwdb-entries/config_hwdb_f2_latest.yaml}"
firesim_args=("$@")

require_file() {
  local path="$1"
  if [ ! -f "${path}" ]; then
    echo "Missing required file: ${path}" >&2
    exit 1
  fi
}

require_file "${FS_DIR}/sourceme-manager.sh"
require_file "${BUILD_RECIPES}"
require_file "${BUILD_CONFIG}"
require_file "${BUILD_HWDB}"

cd "${FS_DIR}"

# FireSim's environment scripts are not nounset-safe. Clear the positional
# arguments while sourcing so FireSim does not interpret this script's args.
set --
set +u
# shellcheck disable=SC1091
source sourceme-manager.sh
set -u

if [ ! -r "${HOME}/firesim.pem" ]; then
  echo "Missing ${HOME}/firesim.pem. Finish setting up this FireSim manager first." >&2
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "The AWS CLI is unavailable. Finish setting up this FireSim manager first." >&2
  exit 1
fi

aws_region="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
if [ -z "${aws_region}" ]; then
  aws_region="$(aws configure get region 2>/dev/null || true)"
fi
if [ -z "${aws_region}" ]; then
  echo "No AWS region is configured. Set AWS_DEFAULT_REGION or configure a default region." >&2
  exit 1
fi
export AWS_DEFAULT_REGION="${aws_region}"

if ! aws sts get-caller-identity >/dev/null; then
  echo "No usable AWS identity was found. Attach the FireSim IAM role to this manager or configure another standard AWS credential provider." >&2
  exit 1
fi

echo "Initializing the FireSim manager for AWS F2 in ${AWS_DEFAULT_REGION}."
printf '%s\n' "${FIRESIM_NOTIFICATION_EMAIL:-}" | firesim managerinit --platform f2

echo "Launching the F2 build farm described by ${BUILD_CONFIG}"
echo "Build recipes: ${BUILD_RECIPES}"
build_started_at="$(date +%s)"

# Additional arguments are passed through to FireSim, for example
# --forceterminate when recovering from an interrupted build.
firesim buildbitstream \
  --buildrecipesconfigfile "${BUILD_RECIPES}" \
  --buildconfigfile "${BUILD_CONFIG}" \
  --hwdbconfigfile "${BUILD_HWDB}" \
  "${firesim_args[@]}"

# FireSim writes the newly created AGFI entry for each successful build into
# deploy/built-hwdb-entries. Assemble only entries created by this run so that
# shareagfi cannot publish stale AGFIs from the sample HWDB.
python3 - "${BUILD_CONFIG}" "${FS_DIR}/deploy/built-hwdb-entries" "${PUBLISH_HWDB}" "${build_started_at}" <<'PY'
import sys
from pathlib import Path

import yaml

build_config_path = Path(sys.argv[1])
entries_dir = Path(sys.argv[2])
publish_hwdb_path = Path(sys.argv[3])
build_started_at = int(sys.argv[4])

with build_config_path.open(encoding="utf-8") as build_config_file:
    build_config = yaml.safe_load(build_config_file)

names = build_config.get("agfis_to_share") or []
if not names:
    raise SystemExit(f"No agfis_to_share entries found in {build_config_path}")

publish_hwdb = {}
for name in names:
    entry_path = entries_dir / name
    if not entry_path.is_file():
        raise SystemExit(f"Missing AGFI entry from the completed build: {entry_path}")
    if entry_path.stat().st_mtime < build_started_at:
        raise SystemExit(f"Refusing to publish stale AGFI entry: {entry_path}")

    with entry_path.open(encoding="utf-8") as entry_file:
        entry = yaml.safe_load(entry_file)
    if not isinstance(entry, dict) or list(entry) != [name]:
        raise SystemExit(f"Unexpected AGFI entry in {entry_path}")
    publish_hwdb[name] = entry[name]

publish_hwdb_path.parent.mkdir(parents=True, exist_ok=True)
with publish_hwdb_path.open("w", encoding="utf-8") as publish_hwdb_file:
    yaml.safe_dump(publish_hwdb, publish_hwdb_file, sort_keys=False)

print(f"Wrote freshly built AGFI database: {publish_hwdb_path}")
PY

echo "Sharing the newly built AGFIs using ${PUBLISH_HWDB}"
firesim shareagfi \
  --buildrecipesconfigfile "${BUILD_RECIPES}" \
  --buildconfigfile "${BUILD_CONFIG}" \
  --hwdbconfigfile "${PUBLISH_HWDB}"
