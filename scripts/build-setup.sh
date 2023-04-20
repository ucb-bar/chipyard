#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

CYDIR=$(git rev-parse --show-toplevel)

# get helpful utilities
source $CYDIR/scripts/utils.sh

common_setup

usage() {
    echo "Usage: ${0} [OPTIONS] [riscv-tools | esp-tools]"
    echo ""
    echo "Installation Types"
    echo "  riscv-tools: if set, builds the riscv toolchain (this is also the default)"
    echo "  esp-tools: if set, builds esp-tools toolchain used for the hwacha vector accelerator"
    echo ""
    echo "Helper script to fully initialize repository that wraps other scripts."
    echo "By default it initializes/installs things in the following order:"
    echo "   1. Conda environment"
    echo "   2. Chipyard submodules"
    echo "   3. Toolchain collateral (Spike, PK, tests, libgloss)"
    echo "   4. Ctags"
    echo "   5. Chipyard pre-compile sources"
    echo "   6. FireSim"
    echo "   7. FireSim pre-compile sources"
    echo "   8. FireMarshal"
    echo "   9. FireMarshal pre-compile default buildroot Linux sources"
    echo "  10. Runs repository clean-up"
    echo ""
    echo "**See below for options to skip parts of the setup. Skipping parts of the setup is not guaranteed to be tested/working.**"
    echo ""
    echo "Options"
    echo "  --help -h               : Display this message"

    echo "  --force -f              : Skip all prompts and checks"
    echo "  --skip-validate         : DEPRECATED: Same functionality as --force"
    echo "  --verbose -v            : Verbose printout"

    echo "  --use-unpinned-deps -ud : Use unpinned conda environment"

    echo "  --skip -s N             : Skip step N in the list above. Use multiple times to skip multiple steps ('-s N -s M ...')."

    exit "$1"
}

TOOLCHAIN_TYPE="riscv-tools"
FORCE_FLAG=""
VERBOSE=false
VERBOSE_FLAG=""
USE_UNPINNED_DEPS=false
SKIP_LIST=()

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | --help )
            usage 3 ;;
        riscv-tools | esp-tools)
            TOOLCHAIN_TYPE=$1 ;;
        --force | -f | --skip-validate)
            FORCE_FLAG=$1 ;;
        --verbose | -v)
            VERBOSE_FLAG=$1
            set -x ;;
        -ud | --use-unpinned-deps )
            USE_UNPINNED_DEPS=true ;;
        --skip | -s)
            shift
            SKIP_LIST+=(${1}) ;;
        * )
            error "invalid option $1"
            usage 1 ;;
    esac
    shift
done

# return true if the arg is not found in the SKIP_LIST
run_step() {
    local value=$1
    [[ ! " ${SKIP_LIST[*]} " =~ " ${value} " ]]
}

{

# esp-tools should ONLY be used for hwacha.
# Check for this, since many users will be attempting to use this with gemmini
if [ $TOOLCHAIN_TYPE == "esp-tools" ]; then
    while true; do
        printf '\033[2J'
        read -p "WARNING: You are trying to install the esp-tools toolchain."$'\n'"This should ONLY be used for Hwacha development."$'\n'"Gemmini should be used with riscv-tools."$'\n'"Type \"y\" to continue if this is intended, or \"n\" if not: " validate
        case "$validate" in
            y | Y)
                echo "Installing esp-tools."
                break
                ;;
            n | N)
                error "Rerun with riscv-tools"
                exit 3
                ;;
            *)
                error "Invalid response. Please type \"y\" or \"n\""
                ;;
        esac
    done
fi

# setup and install conda environment
if run_step "1"; then
    # note: lock file must end in .conda-lock.yml - see https://github.com/conda-incubator/conda-lock/issues/154
    CONDA_REQS=$CYDIR/conda-reqs
    CONDA_LOCK_REQS=$CONDA_REQS/conda-lock-reqs
    LOCKFILE=$CONDA_LOCK_REQS/conda-requirements-$TOOLCHAIN_TYPE-linux-64.conda-lock.yml

    if [ "$USE_UNPINNED_DEPS" = true ]; then
        # auto-gen the lockfile
        conda-lock -f $CONDA_REQS/chipyard.yaml -f $CONDA_REQS/$TOOLCHAIN_TYPE.yaml --lockfile $LOCKFILE
    fi

    # use conda-lock to create env
    conda-lock install -p $CYDIR/.conda-env $LOCKFILE

    source $CYDIR/.conda-env/etc/profile.d/conda.sh
    conda activate $CYDIR/.conda-env
fi

if [ -z "$FORCE_FLAG" ]; then
    if [ -z ${CONDA_DEFAULT_ENV+x} ]; then
        error "ERROR: No conda environment detected. Did you activate the conda environment (e.x. 'conda activate base')?"
        exit 1
    fi
fi

# initialize all submodules (without the toolchain submodules)
if run_step "2"; then
    $CYDIR/scripts/init-submodules-no-riscv-tools.sh $FORCE_FLAG
    $CYDIR/scripts/init-fpga.sh $FORCE_FLAG
fi

# build extra toolchain collateral (i.e. spike, pk, riscv-tests, libgloss)
if run_step "3"; then
    if run_step "1"; then
        PREFIX=$CONDA_PREFIX/$TOOLCHAIN_TYPE
    else
        if [ -z "$RISCV" ] ; then
            error "ERROR: If conda initialization skipped, \$RISCV variable must be defined."
            exit 1
        fi
        PREFIX=$RISCV
    fi
    $CYDIR/scripts/build-toolchain-extra.sh $TOOLCHAIN_TYPE -p $PREFIX
fi

# run ctags for code navigation
if run_step "4"; then
    $CYDIR/scripts/gen-tags.sh
fi

# precompile chipyard scala sources
if run_step "5"; then
    pushd $CYDIR/sims/verilator
    make launch-sbt SBT_COMMAND=";project chipyard; compile"
    make launch-sbt SBT_COMMAND=";project tapeout; compile"
    popd
fi

# setup firesim
if run_step "6"; then
    $CYDIR/scripts/firesim-setup.sh
    $CYDIR/sims/firesim/gen-tags.sh

    # precompile firesim scala sources
    if run_step "7"; then
        pushd $CYDIR/sims/firesim
        (
            echo $CYDIR
            source sourceme-f1-manager.sh --skip-ssh-setup
            pushd sim
            make sbt SBT_COMMAND="project {file:$CYDIR}firechip; compile" TARGET_PROJECT=firesim
            popd
        )
        popd
    fi
fi

# setup firemarshal
if run_step "8"; then
    pushd $CYDIR/software/firemarshal
    ./init-submodules.sh

    # precompile firemarshal buildroot sources
    if run_step "9"; then
        source $CYDIR/scripts/fix-open-files.sh
        ./marshal $VERBOSE_FLAG build br-base.json
        ./marshal $VERBOSE_FLAG clean br-base.json
    fi
    popd
fi

# do misc. cleanup for a "clean" git status
if run_step "10"; then
    $CYDIR/scripts/repo-clean.sh
fi

cat <<EOT >> env.sh
# line auto-generated by $0
conda activate $CYDIR/.conda-env
source $CYDIR/scripts/fix-open-files.sh
EOT

echo "Setup complete!"

} 2>&1 | tee build-setup.log
