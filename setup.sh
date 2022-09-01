#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

# On macOS, use GNU readlink from 'coreutils' package in Homebrew/MacPorts
if [ "$(uname -s)" = "Darwin" ] ; then
    READLINK=greadlink
else
    READLINK=readlink
fi

# If BASH_SOURCE is undefined, we may be running under zsh, in that case
# provide a zsh-compatible alternative
DIR="$(dirname "$($READLINK -f "${BASH_SOURCE[0]:-${(%):-%x}}")")"

usage() {
    echo "Usage: ${0} [OPTIONS] [riscv-tools | esp-tools]"
    echo ""
    echo "Helper script to initialize repository that wraps other scripts."
    echo "Sets up conda environment, initializes submodules, and installs toolchain collateral."
    echo ""
    echo "Installation Types"
    echo "  riscv-tools: if set, builds the riscv toolchain (this is also the default)"
    echo "  esp-tools: if set, builds esp-tools toolchain used for the hwacha vector accelerator"
    echo ""
    echo "Options"
    echo "  --help -h           : Display this message"
    echo "  --env-name -en      : Conda environment name to use"
    echo "  --unpinned-deps -ud : Use unpinned conda environment"
    echo "  --skip-validate     : Skip prompt checking for tagged release/conda"
    exit "$1"
}

TOOLCHAIN="riscv-tools"
CONDA_ENV_NAME="chipyard"
USE_PINNED_DEPS=true
SKIP_VALIDATE_FLAG=""

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | --help )
            usage 3 ;;
        riscv-tools | esp-tools)
            TOOLCHAIN=$1 ;;
        -en | --env-name )
            shift
            ENV_NAME=$1 ;;
        -ud | --unpinned-deps )
            USE_PINNED_DEPS=false ;;
        --skip-validate)
            SKIP_VALIDATE_FLAG=$1 ;;
        * )
            error "invalid option $1"
            usage 1 ;;
    esac
    shift
done

if [ "$SKIP_VALIDATE" = false ]; then
    if [ -z ${CONDA_DEFAULT_ENV+x} ]; then
        error "ERROR: No conda environment detected. Did you activate the conda environment (e.x. 'conda activate base')?"
        exit 1
    fi
fi

# note: lock file must end in .conda-lock.yml - see https://github.com/conda-incubator/conda-lock/issues/154
LOCKFILE=$DIR/conda-requirements-$TOOLCHAIN-linux-64.conda-lock.yml
YAMLFILE=$DIR/conda-requirements-$TOOLCHAIN.yaml

if [ "$USE_PINNED_DEPS" = true ]; then
    # use conda-lock to create env
    conda-lock install -n $ENV_NAME $LOCKFILE
else
    # auto-gen the lockfile
    conda-lock -f $YAMLFILE -p linux-64 --lockfile $LOCKFILE
    # use conda-lock to create env
    conda-lock install -n $ENV_NAME $LOCKFILE
fi

eval "$(conda shell.bash hook)"
conda activate $ENV_NAME

$DIR/scripts/init-submodules-no-riscv-tools.sh $SKIP_VALIDATE_FLAG
$DIR/scripts/build-toolchain-extra.sh $SKIP_VALIDATE_FLAG $TOOLCHAIN
