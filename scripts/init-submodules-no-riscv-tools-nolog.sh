#!/usr/bin/env bash

# Strict mode: exit on error, unset vars, and fail on pipe errors
set -euo pipefail

RDIR=$(git rev-parse --show-toplevel)

# get helpful utilities
source $RDIR/scripts/utils.sh

common_setup

# Custom error handler function
error_handler() {
    local exit_code=$?
    local line_number=$1
    local submodule_name=$2
    echo "Error occurred at line $line_number with exit code $exit_code in \`init-submodules-no-riscv-tools-nolog.sh\`."
    if [ -n "$submodule_name" ]; then
        echo "Submodule $submodule_name failed to update."
    fi
    echo "Exiting script."
    exit $exit_code
}

# Set the trap for catching errors - call the error_handler and pass in the line number on any non-zero exit status
trap 'error_handler $LINENO "$submodule_name"' ERR

function usage
{
    echo "Usage: $0 <options>"
    echo "Initialize Chipyard submodules"
    echo "By default, this will only initialize minimally required submodules"
    echo "Enable other submodules with the --full or submodule-specific flags"
    echo ""
    echo "Options:"
    echo "  -h            Display this help message"
    echo "  --full        Initialize all submodules"
    echo "  --ara         Initialize the optional ara vector-unit submodule"
    echo "  --compressacc Initialize the optional compressor accelerator submodule"
    echo "  --mempress    Initialize the optional mempress accelerator submodule"
    echo "  --saturn      Initialize the optional saturn vector-unit submodule"
    echo ""
}

ENABLE_ARA=0
ENABLE_CALIPTRA=0
ENABLE_COMPRESSACC=0
ENABLE_MEMPRESS=0
ENABLE_SATURN=0

while test $# -gt 0
do
   case "$1" in
        -h | -H | --help | help)
            usage
            exit 1
            ;;
        --force | -f | --skip-validate) # Deprecated flags
            ;;
	--full)
	    ENABLE_ARA=1
	    ENABLE_CALIPTRA=1
	    ENABLE_COMPRESSACC=1
	    ENABLE_MEMPRESS=1
	    ENABLE_SATURN=1
	    ;;
	--ara)
	    ENABLE_ARA=1
	    ;;
	--caliptra)
	    ENABLE_CALIPTRA=1
	    ;;
	--compressacc)
	    ENABLE_COMPRESSACC=1
	    ;;
	--mempress)
	    ENABLE_MEMPRESS=1
	    ;;
	--saturn)
	    ENABLE_SATURN=1
	    ;;
        *)
            echo "ERROR: bad argument $1"
            usage
            exit 2
            ;;
    esac
    shift
done

# check that git version is at least 1.7.8
MYGIT=$(git --version)
MYGIT=${MYGIT#'git version '} # Strip prefix
case ${MYGIT} in
    [1-9]*)
        ;;
    *)
        echo "WARNING: unknown git version"
        ;;
esac
MINGIT="1.8.5"
if [ "$MINGIT" != "$(echo -e "$MINGIT\n$MYGIT" | sort -V | head -n1)" ]; then
  echo "This script requires git version $MINGIT or greater. Exiting."
  exit 4
fi

:

# before doing anything verify that you are on a release branch/tag
save_bash_options
set +e

cd "$RDIR"

(
    # Blocklist of submodules to initially skip:
    # - Toolchain submodules
    # - Generators with huge submodules (e.g., linux sources)
    # - FireSim until explicitly requested
    # - Hammer tool plugins
    git_submodule_exclude() {
        # Call the given subcommand (shell function) on each submodule
        # path to temporarily exclude during the recursive update
        for name in \
            toolchains/*-tools/* \
	    toolchains/libgloss \
	    generators/cva6 \
            generators/ara \
	    generators/caliptra-aes-acc \
	    generators/compress-acc \
            generators/nvdla \
	    generators/mempress \
            generators/gemmini \
            generators/rocket-chip \
	    generators/saturn \
            generators/compress-acc \
            generators/vexiiriscv \
            sims/firesim \
            software/nvdla-workload \
            software/coremark \
            software/firemarshal \
            software/spec2017 \
            software/zephyrproject/zephyr \
            tools/dsptools \
            tools/rocket-dsp-utils \
            tools/circt \
            vlsi/hammer-mentor-plugins
        do
            "$1" "${name%/}"
        done
    }

    _skip() { git config --local "submodule.${1}.update" none ; }
    _unskip() { git config --local --unset-all "submodule.${1}.update" || : ; }

    trap 'git_submodule_exclude _unskip' EXIT INT TERM
    (
        set -x
        git_submodule_exclude _skip
        git submodule update --init --recursive || exit 1
    )
)

(
    # Non-recursive clone to exclude cva6 submods
    submodule_name="generators/cva6"
    git submodule update --init generators/cva6 || exit 1
    git -C generators/cva6 submodule update --init src/main/resources/cva6/vsrc/cva6 || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/axi || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/axi_riscv_atomics || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/common_cells || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/fpga-support || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/riscv-dbg || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/register_interface || exit 1
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init --recursive src/fpu || exit 1
    
    # Non-recursive clone to exclude nvdla submods
    submodule_name="generators/nvdla"
    git submodule update --init generators/nvdla || exit 1
    git -C generators/nvdla submodule update --init src/main/resources/hw || exit 1

    # Optional clones
    if [[ "$ENABLE_ARA" -eq 1 ]] ; then
	git submodule update --init generators/ara || exit 1
	git -C generators/ara submodule update --init ara || exit 1
    fi

    if [[ "$ENABLE_CALIPTRA" -eq 1 ]] ; then
	git submodule update --init generators/caliptra-aes-acc || exit 1
    fi

    if [[ "$ENABLE_COMPRESSACC" -eq 1 ]] ; then
	git submodule update --init generators/compress-acc || exit 1
    fi

    if [[ "$ENABLE_MEMPRESS" -eq 1 ]] ; then
	git submodule update --init generators/mempress || exit 1
    fi

    if [[ "$ENABLE_SATURN" -eq 1 ]] ; then
	git submodule update --init --recursive generators/saturn || exit 1
    fi
    
    # Non-recursive clone to exclude gemmini-software
    submodule_name="generators/gemmini"
    git submodule update --init generators/gemmini || exit 1
    git -C generators/gemmini/ submodule update --init --recursive software/gemmini-rocc-tests || exit 1

    # Non-recursive clone
    submodule_name="generators/rocket-chip"
    git submodule update --init generators/rocket-chip || exit 1


    # Non-recursive clone
    submodule_name="generators/vexiiriscv"
    git submodule update --init generators/vexiiriscv || exit 1
    git -C generators/vexiiriscv submodule update --init VexiiRiscv || exit 1
    git -C generators/vexiiriscv/VexiiRiscv submodule update --init ext/SpinalHDL || exit 1
    git -C generators/vexiiriscv/VexiiRiscv submodule update --init ext/rvls || exit 1

    # Minimal non-recursive clone to initialize sbt dependencies
    submodule_name="sims/firesim"
    git submodule update --init sims/firesim || exit 1
    git config --local submodule.sims/firesim.update none

    # Non-recursive clone
    submodule_name="tools/rocket-dsp-utils"
    git submodule update --init tools/rocket-dsp-utils || exit 1

    # Non-recursive clone
    submodule_name="tools/dsptools"
    git submodule update --init tools/dsptools || exit 1

    # Only shallow clone needed for basic SW tests
    submodule_name="software/firemarshal"
    git submodule update --init software/firemarshal || exit 1
)

# Configure firemarshal to know where our firesim installation is
if [ ! -f ./software/firemarshal/marshal-config.yaml ]; then
  echo "firesim-dir: '../../sims/firesim/'" > ./software/firemarshal/marshal-config.yaml
fi
