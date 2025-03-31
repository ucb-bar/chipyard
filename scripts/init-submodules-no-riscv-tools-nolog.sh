#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)

# get helpful utilities
source $RDIR/scripts/utils.sh

common_setup

# Custom error handler function
error_handler() {
    local exit_code=$?
    local line_number=$1
    echo "Error occurred at line $line_number with exit code $exit_code in \`init-submodules-no-riscv-tools-nolog.sh\`."
    echo "Exiting script."
    exit $exit_code
}

# Set the trap for catching errors - call the error_handler and pass in the line number on any non-zero exit status
trap 'error_handler $LINENO' ERR

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
        git submodule update --init --recursive || { echo "Error: Failed to update submodules recursively."; exit 1; }
    )
)

(
    # Non-recursive clone to exclude cva6 submods
    git submodule update --init generators/cva6 || { echo "Error: Failed to update generators/cva6 submodule."; exit 1; }
    git -C generators/cva6 submodule update --init src/main/resources/cva6/vsrc/cva6 || { echo "Error: Failed to update cva6 submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/axi || { echo "Error: Failed to update axi submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/axi_riscv_atomics || { echo "Error: Failed to update axi_riscv_atomics submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/common_cells || { echo "Error: Failed to update common_cells submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/fpga-support || { echo "Error: Failed to update fpga-support submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/riscv-dbg || { echo "Error: Failed to update riscv-dbg submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init src/register_interface || { echo "Error: Failed to update register_interface submodule."; exit 1; }
    git -C generators/cva6/src/main/resources/cva6/vsrc/cva6 submodule update --init --recursive src/fpu || { echo "Error: Failed to update fpu submodule."; exit 1; }
    
    # Non-recursive clone to exclude nvdla submods
    git submodule update --init generators/nvdla || { echo "Error: Failed to update generators/nvdla submodule."; exit 1; }
    git -C generators/nvdla submodule update --init src/main/resources/hw || { echo "Error: Failed to update hw submodule in nvdla."; exit 1; }

    # Non-recursive clone to exclude ara submods
    git submodule update --init generators/ara || { echo "Error: Failed to update generators/ara submodule."; exit 1; }
    git -C generators/ara submodule update --init ara || { echo "Error: Failed to update ara submodule."; exit 1; }

    # Non-recursive clone to exclude gemmini-software
    git submodule update --init generators/gemmini || { echo "Error: Failed to update generators/gemmini submodule."; exit 1; }
    git -C generators/gemmini/ submodule update --init --recursive software/gemmini-rocc-tests || { echo "Error: Failed to update gemmini-rocc-tests submodule."; exit 1; }

    # Non-recursive clone
    git submodule update --init generators/rocket-chip || { echo "Error: Failed to update generators/rocket-chip submodule."; exit 1; }

    # Non-recursive clone
    git submodule update --init generators/compress-acc || { echo "Error: Failed to update generators/compress-acc submodule."; exit 1; }

    # Non-recursive clone
    git submodule update --init generators/vexiiriscv || { echo "Error: Failed to update generators/vexiiriscv submodule."; exit 1; }
    git -C generators/vexiiriscv submodule update --init VexiiRiscv || { echo "Error: Failed to update VexiiRiscv submodule."; exit 1; }
    git -C generators/vexiiriscv/VexiiRiscv submodule update --init ext/SpinalHDL || { echo "Error: Failed to update SpinalHDL submodule."; exit 1; }
    git -C generators/vexiiriscv/VexiiRiscv submodule update --init ext/rvls || { echo "Error: Failed to update rvls submodule."; exit 1; }

    # Minimal non-recursive clone to initialize sbt dependencies
    git submodule update --init sims/firesim || { echo "Error: Failed to update sims/firesim submodule."; exit 1; }
    git config --local submodule.sims/firesim.update none

    # Non-recursive clone
    git submodule update --init tools/rocket-dsp-utils || { echo "Error: Failed to update tools/rocket-dsp-utils submodule."; exit 1; }

    # Non-recursive clone
    git submodule update --init tools/dsptools || { echo "Error: Failed to update tools/dsptools submodule."; exit 1; }

    # Only shallow clone needed for basic SW tests
    git submodule update --init software/firemarshal || { echo "Error: Failed to update software/firemarshal submodule."; exit 1; }
)

# Configure firemarshal to know where our firesim installation is
if [ ! -f ./software/firemarshal/marshal-config.yaml ]; then
  echo "firesim-dir: '../../sims/firesim/'" > ./software/firemarshal/marshal-config.yaml
fi

replace_content env.sh init-submodules "# line auto-generated by init-submodules-no-riscv-tools.sh
__DIR=\"$RDIR\"
PATH=\$__DIR/software/firemarshal:\$PATH"
