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
