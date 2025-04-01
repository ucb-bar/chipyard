#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)

# get helpful utilities
source $RDIR/scripts/utils.sh

common_setup

function usage
{
    echo "Usage: $0"
    echo "Initialize Chipyard submodules and setup initial env.sh script."
    echo ""
}

while test $# -gt 0
do
   case "$1" in
        -h | -H | --help | help)
            usage
            exit 1
            ;;
        --force | -f | --skip-validate) # Deprecated flags
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
            generators/cva6 \
            generators/ara \
            generators/nvdla \
            toolchains/libgloss \
            generators/gemmini \
            generators/rocket-chip \
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
        git submodule update --init --recursive #--jobs 8
    )
)

(
    # Non-recursive clone to exclude cva6 submods
    git submodule update --init generators/cva6
    git -C generators/cva6 submodule update --init src/main/resources/cva6/vsrc/cva6
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/axi
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/axi_riscv_atomics
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/common_cells
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/fpga-support
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/riscv-dbg
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/register_interface
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init --recursive src/fpu
    # Non-recursive clone to exclude nvdla submods
    git submodule update --init generators/nvdla
    git -C generators/nvdla submodule update --init src/main/resources/hw

    # Non-recursive clone to exclude ara submods
    git submodule update --init generators/ara
    git -C generators/ara submodule update --init ara

    # Non-recursive clone to exclude gemmini-software
    git submodule update --init generators/gemmini
    git -C generators/gemmini/ submodule update --init --recursive software/gemmini-rocc-tests

    # Non-recursive clone
    git submodule update --init generators/rocket-chip

    # Non-recursive clone
    git submodule update --init generators/compress-acc

    # Non-recursive clone
    git submodule update --init generators/vexiiriscv
    git -C generators/vexiiriscv submodule update --init VexiiRiscv
    git -C generators/vexiiriscv/VexiiRiscv submodule update --init ext/SpinalHDL
    git -C generators/vexiiriscv/VexiiRiscv submodule update --init ext/rvls

    # Minimal non-recursive clone to initialize sbt dependencies
    git submodule update --init sims/firesim
    git config --local submodule.sims/firesim.update none

    # Non-recursive clone
    git submodule update --init tools/rocket-dsp-utils

    # Non-recursive clone
    git submodule update --init tools/dsptools

    # Only shallow clone needed for basic SW tests
    git submodule update --init software/firemarshal
)

# Configure firemarshal to know where our firesim installation is
if [ ! -f ./software/firemarshal/marshal-config.yaml ]; then
  echo "firesim-dir: '../../sims/firesim/'" > ./software/firemarshal/marshal-config.yaml
fi

replace_content env.sh init-submodules "# line auto-generated by init-submodules-no-riscv-tools.sh
__DIR="$RDIR"
PATH=\$__DIR/software/firemarshal:\$PATH"
