#/usr/bin/env bash

#######################################
# Common setup. Init MacOS compatibility
# variables.
# Globals:
#   READLINK
#######################################
function common_setup
{
    # On macOS, use GNU readlink from 'coreutils' package in Homebrew/MacPorts
    if [ "$(uname -s)" = "Darwin" ] ; then
        READLINK=greadlink
    else
        READLINK=readlink
    fi
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
    # On macOS, use GNU readlink from 'coreutils' package in Homebrew/MacPorts
    if [ "$(uname -s)" = "Darwin" ] ; then
        READLINK=greadlink
    else
        READLINK=readlink
    fi

    # If BASH_SOURCE is undefined, we may be running under zsh, in that case
    # provide a zsh-compatible alternative
    DIR="$(dirname "$($READLINK -f "${BASH_SOURCE[0]:-${(%):-%x}}")")"
    file="$1"
    shift
    key="$1"
    shift
    $DIR/replace-content.py "$file" "$key" "$@"
}
