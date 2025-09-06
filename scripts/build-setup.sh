#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

CYDIR=$(git rev-parse --show-toplevel)

# get helpful utilities
source $CYDIR/scripts/utils.sh

common_setup

usage() {
    echo "Usage: ${0} [OPTIONS] [riscv-tools]"
    echo ""
    echo "Installation Types"
    echo "  riscv-tools: if set, builds the riscv toolchain (this is also the default)"
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
    echo "  10. Install CIRCT"
    echo "  11. Runs repository clean-up"
    echo ""
    echo "**See below for options to skip parts of the setup. Skipping parts of the setup is not guaranteed to be tested/working.**"
    echo ""
    echo "Options"
    echo "  --help -h               : Display this message"
    echo "  --verbose -v            : Verbose printout"
    echo "  --use-unpinned-deps -ud : Use unpinned conda environment"
    echo "  --use-lean-conda        : Install a leaner version of the repository (smaller conda env, no FireSim, no FireMarshal)"
    echo "  --build-circt           : Builds CIRCT from source, instead of downloading the precompiled binary"
    echo "  --conda-env-name NAME   : Optionally use a global conda env name instead of storing env in Chipyard directory"
    echo "  --github-token TOKEN    : Optionally use a Github token to download CIRCT"

    echo "  --skip -s N             : Skip step N in the list above. Use multiple times to skip multiple steps ('-s N -s M ...')."
    echo "  --skip-conda            : Skip Conda initialization (step 1)"
    echo "  --skip-submodules       : Skip submodule initialization (step 2)"
    echo "  --skip-toolchain        : Skip toolchain collateral (step 3)"
    echo "  --skip-ctags            : Skip ctags (step 4)"
    echo "  --skip-precompile       : Skip precompiling sources (steps 5/7)"
    echo "  --skip-firesim          : Skip Firesim initialization (steps 6/7)"
    echo "  --skip-marshal          : Skip firemarshal initialization (steps 8/9)"
    echo "  --skip-circt            : Skip CIRCT install (step 10)"
    echo "  --skip-clean            : Skip repository clean-up (step 11)"

    exit "$1"
}

TOOLCHAIN_TYPE="riscv-tools"
VERBOSE=false
VERBOSE_FLAG=""
USE_UNPINNED_DEPS=false
USE_LEAN_CONDA=false
SKIP_LIST=()
BUILD_CIRCT=false
GLOBAL_ENV_NAME=""
GITHUB_TOKEN="null"

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | --help )
            usage 3 ;;
        riscv-tools )
            TOOLCHAIN_TYPE=$1 ;;
        --verbose | -v)
            VERBOSE_FLAG=$1
            set -x ;;
        --use-lean-conda)
            USE_LEAN_CONDA=true
            SKIP_LIST+=(4 6 7 8 9) ;;
        --build-circt)
            BUILD_CIRCT=true ;;
        --conda-env-name)
            shift
            GLOBAL_ENV_NAME=${1} ;;
        --github-token)
            shift
            GITHUB_TOKEN=${1} ;;
        -ud | --use-unpinned-deps )
            USE_UNPINNED_DEPS=true ;;
        --skip | -s)
            shift
            SKIP_LIST+=(${1}) ;;
        --skip-conda)
            SKIP_LIST+=(1) ;;
        --skip-submodules)
            SKIP_LIST+=(2) ;;
        --skip-toolchain)
            SKIP_LIST+=(3) ;;
        --skip-ctags)
            SKIP_LIST+=(4) ;;
        --skip-precompile)
            SKIP_LIST+=(5 6) ;;
        --skip-firesim)
            SKIP_LIST+=(6 7) ;;
        --skip-marshal)
            SKIP_LIST+=(8 9) ;;
        --skip-circt)
            SKIP_LIST+=(10) ;;
        --skip-clean)
            SKIP_LIST+=(11) ;;
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

#######################################
###### BEGIN STEP-BY-STEP SETUP #######
#######################################

# In order to run code on error, we must handle errors manually
set +e;

function begin_step
{
    thisStepNum=$1;
    thisStepDesc=$2;
    echo " ========== BEGINNING STEP $thisStepNum: $thisStepDesc =========="
}
function exit_if_last_command_failed
{
    local exitcode=$?;
    if [ $exitcode -ne 0 ]; then
        die "Build script failed with exit code $exitcode at step $thisStepNum: $thisStepDesc" $exitcode;
    fi
}

# add helper variable pointing to current chipyard top-level dir
replace_content env.sh cy-dir-helper "CY_DIR=${CYDIR}"

# setup and install conda environment
if run_step "1"; then
    begin_step "1" "Conda environment setup"
    # note: lock file must end in .conda-lock.yml - see https://github.com/conda-incubator/conda-lock/issues/154
    CONDA_REQS=$CYDIR/conda-reqs
    CONDA_LOCK_REQS=$CONDA_REQS/conda-lock-reqs
    # must match with the file generated by generate-conda-lockfile.sh
    if [ "$USE_LEAN_CONDA" = false ]; then
      LOCKFILE=$CONDA_LOCK_REQS/conda-requirements-$TOOLCHAIN_TYPE-linux-64.conda-lock.yml
    else
      LOCKFILE=$CONDA_LOCK_REQS/conda-requirements-$TOOLCHAIN_TYPE-linux-64-lean.conda-lock.yml
    fi

    # create conda-lock only environment to be used in this section.
    # done with cloning base then installing conda lock to speed up dependency solving.
    CONDA_LOCK_ENV_PATH=$CYDIR/.conda-lock-env

    # check if directories already exist
    if [ -d $CONDA_LOCK_ENV_PATH ] || [ -d "$CYDIR/.conda-env" ]; then
        echo "Error: Conda environment directories already exist! Delete them before trying to recreate the \
conda environment or \`source env.sh\` and skip this step with \`-s 1\`." >&2
        exit 1
    fi
    
    rm -rf $CONDA_LOCK_ENV_PATH &&
    conda create -y -p $CONDA_LOCK_ENV_PATH -c conda-forge $(grep "conda-lock" $CONDA_REQS/chipyard-base.yaml | sed 's/^ \+-//') &&
    source $(conda info --base)/etc/profile.d/conda.sh &&
    conda activate $CONDA_LOCK_ENV_PATH
    exit_if_last_command_failed

    if [ "$USE_UNPINNED_DEPS" = true ]; then
        # auto-gen the lockfiles
        $CYDIR/scripts/generate-conda-lockfiles.sh
        exit_if_last_command_failed
    fi
    SYS_GLIBC=$(ldd --version | awk '/ldd/{print $NF}')
    DEFAULT_GLIBC=$(grep -i "sysroot_linux-64=" conda-reqs/chipyard-base.yaml | awk -F= '{print $2}')
    if [ "$SYS_GLIBC" != "$DEFAULT_GLIBC" ]; then
        # replace the glibc version
        sed -i.bak "s/^\([[:space:]]*-\s*sysroot_linux-64=\).*/\1$SYS_GLIBC/" conda-reqs/chipyard-base.yaml
        $CYDIR/scripts/generate-conda-lockfiles.sh
        exit_if_last_command_failed
    fi
    echo "Using lockfile for conda: $LOCKFILE"

    # use conda-lock to create env
    if [ -z "$GLOBAL_ENV_NAME" ] ; then
        CONDA_ENV_PATH=$CYDIR/.conda-env
        CONDA_ENV_ARG="-p $CONDA_ENV_PATH"
        CONDA_ENV_NAME=$CONDA_ENV_PATH
    else
        CONDA_ENV_ARG="-n $GLOBAL_ENV_NAME"
        CONDA_ENV_NAME=$GLOBAL_ENV_NAME
    fi
    echo "Storing main conda environment in $CONDA_ENV_NAME"

    conda-lock install --conda $CONDA_EXE $CONDA_ENV_ARG $LOCKFILE &&
    ## If the above line errors in your environment, you can try the line below
    # conda-lock install --conda $(which conda) $CONDA_ENV_ARG $LOCKFILE &&
    source $(conda info --base)/etc/profile.d/conda.sh &&
    conda activate $CONDA_ENV_NAME
    exit_if_last_command_failed

    # Conda Setup
    # Provide a sourceable snippet that can be used in subshells that may not have
    # inhereted conda functions that would be brought in under a login shell that
    # has run conda init (e.g., VSCode, CI)
    read -r -d '\0' CONDA_ACTIVATE_PREAMBLE <<'END_CONDA_ACTIVATE'
if ! type conda >& /dev/null; then
    echo "::ERROR:: you must have conda in your environment first"
    return 1  # don't want to exit here because this file is sourced
fi

source $(conda info --base)/etc/profile.d/conda.sh
\0
END_CONDA_ACTIVATE

    replace_content env.sh build-setup-conda "# line auto-generated by $0
$CONDA_ACTIVATE_PREAMBLE
conda activate $CONDA_ENV_NAME
source $CYDIR/scripts/fix-open-files.sh"
fi

if [ -z ${CONDA_DEFAULT_ENV+x} ]; then
    echo "!!!!! WARNING: No conda environment detected. Did you activate the conda environment (e.x. 'conda activate base')?"
fi

# initialize all submodules (without the toolchain submodules)
if run_step "2"; then
    begin_step "2" "Initializing Chipyard submodules"
    $CYDIR/scripts/init-submodules-no-riscv-tools.sh --full
    exit_if_last_command_failed
fi

# build extra toolchain collateral (i.e. spike, pk, riscv-tests, libgloss)
if run_step "3"; then
    begin_step "3" "Building toolchain collateral"
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
    exit_if_last_command_failed
fi

# run ctags for code navigation
if run_step "4"; then
    begin_step "4" "Running ctags for code navigation"
    $CYDIR/scripts/gen-tags.sh
    exit_if_last_command_failed
fi

# precompile chipyard scala sources
if run_step "5"; then
    begin_step "5" "Pre-compiling Chipyard Scala sources"
    pushd $CYDIR/sims/verilator &&
    make launch-sbt SBT_COMMAND=";project chipyard; compile" &&
    make launch-sbt SBT_COMMAND=";project tapeout; compile" &&
    popd
    exit_if_last_command_failed
fi

# setup firesim
if run_step "6"; then
    begin_step "6" "Setting up FireSim"
    $CYDIR/scripts/firesim-setup.sh &&
    $CYDIR/sims/firesim/gen-tags.sh
    exit_if_last_command_failed

    # precompile firesim scala sources
    if run_step "7"; then
        begin_step "7" "Pre-compiling Firesim Scala sources"
        pushd $CYDIR/sims/firesim &&
        (
            set -e # Subshells un-set "set -e" so it must be re enabled
            source sourceme-manager.sh --skip-ssh-setup
            pushd sim
            # avoid directly building classpath s.t. target-injected files can be recompiled
            make sbt SBT_COMMAND="compile"
            popd
        )
        exit_if_last_command_failed
        popd
    fi
fi

# setup firemarshal
if run_step "8"; then
    begin_step "8" "Setting up FireMarshal"
    pushd $CYDIR/software/firemarshal &&
    ./init-submodules.sh
    exit_if_last_command_failed

    # precompile firemarshal buildroot sources
    if run_step "9"; then
        begin_step "9" "Pre-compiling FireMarshal buildroot sources"
        source $CYDIR/scripts/fix-open-files.sh &&
        ./marshal $VERBOSE_FLAG build br-base.json &&
        ./marshal $VERBOSE_FLAG build bare-base.json
        exit_if_last_command_failed
    fi
    popd
    # Ensure FireMarshal CLI is on PATH in env.sh (idempotent)
    replace_content env.sh build-setup-marshal "# line auto-generated by build-setup.sh\n__DIR=\"$CYDIR\"\nPATH=\\$__DIR/software/firemarshal:\\$PATH"
fi

if run_step "10"; then
    begin_step "10" "Installing CIRCT"
    # install circt into conda
    if run_step "1"; then
        PREFIX=$CONDA_PREFIX/$TOOLCHAIN_TYPE
    else
        if [ -z "$RISCV" ] ; then
            error "ERROR: If conda initialization skipped, \$RISCV variable must be defined."
            exit 1
        fi
        PREFIX=$RISCV
    fi

    if [ "$BUILD_CIRCT" = true ] ; then
	echo "Building CIRCT from source, and installing to $PREFIX"
	$CYDIR/scripts/build-circt-from-source.sh --prefix $PREFIX
    else
	echo "Downloading CIRCT from nightly build"

	git submodule update --init $CYDIR/tools/install-circt &&
	    $CYDIR/tools/install-circt/bin/download-release-or-nightly-circt.sh \
		-f circt-full-static-linux-x64.tar.gz \
		-i $PREFIX \
		-v version-file \
		-x $CYDIR/conda-reqs/circt.json \
		-g $GITHUB_TOKEN
    fi
    exit_if_last_command_failed
fi


# do misc. cleanup for a "clean" git status
if run_step "11"; then
    begin_step "11" "Cleaning up repository"
    $CYDIR/scripts/repo-clean.sh
    exit_if_last_command_failed
fi

echo "Setup complete!"

} 2>&1 | tee build-setup.log
