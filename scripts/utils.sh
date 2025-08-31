#!/usr/bin/env bash

#######################################
# Common setup. Init MacOS compatibility
# variables.
# Globals:
#   READLINK (best-effort; no GNU requirement)
#######################################
function common_setup
{
    # Prefer system readlink if present; do not require GNU variant
    if command -v readlink >/dev/null 2>&1 ; then
        READLINK=readlink
    else
        READLINK=:
    fi
}

#######################################
# Portable realpath implementation.
# Resolves to an absolute, canonical path without requiring GNU readlink.
# Arguments:
#   $1: path to resolve
#######################################
function realpath_portable
{
    # Prefer python3 if available
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$1" <<'PY'
import os, sys
print(os.path.realpath(sys.argv[1]))
PY
        return
    fi

    # Fallback to perl, commonly available on macOS
    if command -v perl >/dev/null 2>&1; then
        perl -MCwd -e 'print Cwd::abs_path(shift), "\n"' "$1"
        return
    fi

    # Pure shell fallback using readlink (no -f) and cd -P
    target="$1"
    dir="$(dirname -- "$target")"
    base="$(basename -- "$target")"
    [ -d "$target" ] && { dir="$target"; base=""; }

    # Resolve symlinks
    while [ -n "$base" ] && [ -L "$dir/$base" ]; do
        link="$($READLINK "$dir/$base" 2>/dev/null)" || break
        case "$link" in
            /*) target="$link" ;;
             *) target="$dir/$link" ;;
        esac
        dir="$(dirname -- "$target")"
        base="$(basename -- "$target")"
    done

    dir="$(cd -P "$dir" >/dev/null 2>&1 && pwd)"
    [ -n "$base" ] && echo "$dir/$base" || echo "$dir"
}

#######################################
# Error echo wrapper
#######################################
function error
{
    echo "${0##*/}: ${1}" >&2
}

#######################################
# Error then exit wrapper
# Arguments:
#   string to print before exit
#   (optional) int error code
#######################################
function die
{
    error "$1"
    exit "${2:--1}"
}

#######################################
# Save bash options. Must be called
# before a corresponding `restore_bash_options`.
#######################################
function save_bash_options
{
    OLDSTATE=$(set +o)
}

#######################################
# Restore bash options. Must be called
# after a corresponding `save_bash_options`.
#######################################
function restore_bash_options
{
    set +vx; eval "$OLDSTATE"
}

#######################################
# Wrapper around replace-content.py.
# For a file ($1), write out text ($3) into it
# replacing any area designated by $2.
#######################################
function replace_content
{
    # If BASH_SOURCE is undefined, we may be running under zsh, in that case
    # provide a zsh-compatible alternative
    DIR="$(dirname "$(realpath_portable "${BASH_SOURCE[0]:-${(%):-%x}}")")"
    file="$1"
    shift
    key="$1"
    shift
    $DIR/replace-content.py "$file" "$key" "$@"
}
