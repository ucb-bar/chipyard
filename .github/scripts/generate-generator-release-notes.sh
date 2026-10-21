#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: .github/scripts/generate-generator-release-notes.sh [base-tag]

Generate .github/<generator>-release.log for every generator submodule. Each
file contains the submodule's git log from the commit pinned by base-tag to the
commit pinned by the current Chipyard HEAD.

If base-tag is omitted, the nearest tag reachable before HEAD is used. Set
SKIP_FETCH=1 to use only locally available Chipyard tags and submodule refs.
EOF
}

if [ "$#" -gt 1 ]; then
  usage >&2
  exit 2
fi
if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

REPO_DIR="$(git rev-parse --show-toplevel)"
OUTPUT_DIR="${REPO_DIR}/.github"
TARGET_COMMIT="$(git -C "${REPO_DIR}" rev-parse HEAD^{commit})"

if [ "${SKIP_FETCH:-0}" != "1" ]; then
  echo "Fetching Chipyard tags from origin."
  git -C "${REPO_DIR}" fetch --tags origin
fi

if [ -n "${1:-}" ]; then
  BASE_TAG="$1"
else
  if ! BASE_TAG="$(git -C "${REPO_DIR}" describe --tags --abbrev=0 "${TARGET_COMMIT}^")"; then
    echo "Could not find a tag before ${TARGET_COMMIT}. Pass the base tag explicitly." >&2
    exit 1
  fi
fi

BASE_COMMIT="$(git -C "${REPO_DIR}" rev-parse "${BASE_TAG}^{commit}")"
echo "Generating generator release logs for ${BASE_TAG} (${BASE_COMMIT})..HEAD (${TARGET_COMMIT})."

mapfile -t generator_paths < <(
  git -C "${REPO_DIR}" config --file .gitmodules --get-regexp '^submodule\..*\.path$' \
    | awk '$2 ~ /^generators\// { print $2 }' \
    | sort -u
)

if [ "${#generator_paths[@]}" -eq 0 ]; then
  echo "No generator submodules found in .gitmodules." >&2
  exit 1
fi

for generator_path in "${generator_paths[@]}"; do
  generator_name="${generator_path##*/}"
  output_path="${OUTPUT_DIR}/${generator_name}-release.log"

  new_entry="$(git -C "${REPO_DIR}" ls-tree "${TARGET_COMMIT}" -- "${generator_path}")"
  if [ -z "${new_entry}" ] || [ "${new_entry%% *}" != "160000" ]; then
    echo "Expected ${generator_path} to be a submodule at ${TARGET_COMMIT}." >&2
    exit 1
  fi
  new_commit="$(awk '{ print $3 }' <<< "${new_entry}")"

  old_entry="$(git -C "${REPO_DIR}" ls-tree "${BASE_COMMIT}" -- "${generator_path}")"
  if [ -n "${old_entry}" ] && [ "${old_entry%% *}" = "160000" ]; then
    old_commit="$(awk '{ print $3 }' <<< "${old_entry}")"
  else
    old_commit=""
  fi

  if ! git -C "${REPO_DIR}/${generator_path}" rev-parse --git-dir >/dev/null 2>&1; then
    if [ "${SKIP_FETCH:-0}" = "1" ]; then
      echo "${generator_path} is not initialized and SKIP_FETCH=1 was requested." >&2
      exit 1
    fi
    echo "Initializing ${generator_path}."
    git -C "${REPO_DIR}" submodule update --init -- "${generator_path}"
  fi

  if [ "${SKIP_FETCH:-0}" != "1" ]; then
    echo "Fetching ${generator_path} from origin."
    git -C "${REPO_DIR}/${generator_path}" fetch origin
  fi

  if ! git -C "${REPO_DIR}/${generator_path}" cat-file -e "${new_commit}^{commit}" 2>/dev/null; then
    git -C "${REPO_DIR}/${generator_path}" fetch origin "${new_commit}"
  fi

  if [ -n "${old_commit}" ]; then
    if ! git -C "${REPO_DIR}/${generator_path}" cat-file -e "${old_commit}^{commit}" 2>/dev/null; then
      git -C "${REPO_DIR}/${generator_path}" fetch origin "${old_commit}"
    fi
    git -C "${REPO_DIR}/${generator_path}" log --no-color "${old_commit}..${new_commit}" > "${output_path}"
    commit_count="$(git -C "${REPO_DIR}/${generator_path}" rev-list --count "${old_commit}..${new_commit}")"
    echo "Wrote ${output_path} (${commit_count} commits; ${old_commit}..${new_commit})."
  else
    # A generator added after the base tag has no old gitlink. Include all
    # history reachable from the first Chipyard-pinned commit.
    git -C "${REPO_DIR}/${generator_path}" log --no-color "${new_commit}" > "${output_path}"
    commit_count="$(git -C "${REPO_DIR}/${generator_path}" rev-list --count "${new_commit}")"
    echo "Wrote ${output_path} (${commit_count} commits; generator was not present in ${BASE_TAG})."
  fi
done
