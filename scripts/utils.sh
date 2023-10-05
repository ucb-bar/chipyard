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
# Basic try-catch block implementation
# for bash scripts.
# Usage: try; ( run commands )
#  catch || { handle error }
# Source: https://stackoverflow.com/a/25180186/5121242
#######################################
function try()
{
    [[ $- = *e* ]]; SAVED_OPT_E=$?
    set +e
}

function catch()
{
    export ex_code=$?
    (( $SAVED_OPT_E )) && set +e
    return $ex_code
}

