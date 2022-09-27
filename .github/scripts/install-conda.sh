#!/bin/bash

CONDA_INSTALL_PREFIX=/opt/conda
CONDA_INSTALLER_VERSION=4.12.0-0
CONDA_INSTALLER="https://github.com/conda-forge/miniforge/releases/download/${CONDA_INSTALLER_VERSION}/Miniforge3-${CONDA_INSTALLER_VERSION}-Linux-x86_64.sh"
CONDA_CMD="conda" # some installers install mamba or micromamba

DRY_RUN_OPTION=""
DRY_RUN_ECHO=()
REINSTALL_CONDA=0

usage()
{
    echo "Usage: $0 [options]"
    echo
    echo "Options:"
    echo "[--help]                  List this help"
    echo "[--prefix <prefix>]       Install prefix for conda. Defaults to /opt/conda."
    echo "                          If <prefix>/bin/conda already exists, it will be used and install is skipped."
    echo "[--dry-run]               Pass-through to all conda commands and only print other commands."
    echo "                          NOTE: --dry-run will still install conda to --prefix"
    echo "[--reinstall-conda]       Repairs a broken base environment by reinstalling."
    echo "                          NOTE: will only reinstall conda and exit"
    echo
    echo "Examples:"
    echo "  % $0"
    echo "     Install into default system-wide prefix (using sudo if needed) and add install to system-wide /etc/profile.d"
    echo "  % $0 --prefix ~/conda"
    echo "     Install into $HOME/conda"
}


while [ $# -gt 0 ]; do
    case "$1" in
        --help)
            usage
            exit 1
            ;;
        --prefix)
            shift
            CONDA_INSTALL_PREFIX="$1"
            shift
            ;;
        --dry-run)
            shift
            DRY_RUN_OPTION="--dry-run"
            DRY_RUN_ECHO=(echo "Would Run:")
            ;;
        --reinstall-conda)
            shift
            REINSTALL_CONDA=1
            ;;
        *)
            echo "Invalid Argument: $1"
            usage
            exit 1
            ;;
    esac
done

if [[ $REINSTALL_CONDA -eq 1 && -n "$DRY_RUN_OPTION" ]]; then
    echo "::ERROR:: --dry-run and --reinstall-conda are mutually exclusive.  Pick one or the other."
fi

set -ex
set -o pipefail

# uname options are not portable so do what https://www.gnu.org/software/coreutils/faq/coreutils-faq.html#uname-is-system-specific
# suggests and iteratively probe the system type
if ! type uname >&/dev/null; then
    echo "::ERROR:: need 'uname' command available to determine if we support this sytem"
    exit 1
fi

if [[ "$(uname)" != "Linux" ]]; then
    echo "::ERROR:: $0 only supports 'Linux' not '$(uname)'"
    exit 1
fi

if [[ "$(uname -mo)" != "x86_64 GNU/Linux" ]]; then
    echo "::ERROR:: $0 only supports 'x86_64 GNU/Linux' not '$(uname -io)'"
    exit 1
fi

if [[ ! -r /etc/os-release ]]; then
    echo "::ERROR:: $0 depends on /etc/os-release for distro-specific setup and it doesn't exist here"
    exit 1
fi

OS_FLAVOR=$(grep '^ID=' /etc/os-release | awk -F= '{print $2}' | tr -d '"')
OS_VERSION=$(grep '^VERSION_ID=' /etc/os-release | awk -F= '{print $2}' | tr -d '"')

# platform-specific setup
case "$OS_FLAVOR" in
    ubuntu)
        ;;
    centos)
        ;;
    *)
        echo "::ERROR:: Unknown OS flavor '$OS_FLAVOR'. Unable to do platform-specific setup."
        exit 1
        ;;
esac


# everything else is platform-agnostic and could easily be expanded to Windows and/or OSX

SUDO=""
prefix_parent=$(dirname "$CONDA_INSTALL_PREFIX")
if [[ ! -e "$prefix_parent" ]]; then
    mkdir -p "$prefix_parent" || SUDO=sudo
elif [[ ! -w "$prefix_parent" ]]; then
    SUDO=sudo
fi

if [[ -n "$SUDO" ]]; then
    echo "::INFO:: using 'sudo' to install conda"
    # ensure files are read-execute for everyone
    umask 022
fi

if [[ -n "$SUDO"  || "$(id -u)" == 0 ]]; then
    INSTALL_TYPE=system
else
    INSTALL_TYPE=user
fi

# to enable use of sudo and avoid modifying 'secure_path' in /etc/sudoers, we specify the full path to conda
CONDA_EXE="${CONDA_INSTALL_PREFIX}/bin/$CONDA_CMD"

if [[ -x "$CONDA_EXE" && $REINSTALL_CONDA -eq 0 ]]; then
    echo "::INFO:: '$CONDA_EXE' already exists, skipping conda install"
else
    wget -O install_conda.sh "$CONDA_INSTALLER"  || curl -fsSLo install_conda.sh "$CONDA_INSTALLER"
    if [[ $REINSTALL_CONDA -eq 1 ]]; then
        conda_install_extra="-u"
        echo "::INFO:: RE-installing conda to '$CONDA_INSTALL_PREFIX'"
    else
        conda_install_extra=""
        echo "::INFO:: installing conda to '$CONDA_INSTALL_PREFIX'"
    fi
    # -b for non-interactive install
    $SUDO bash ./install_conda.sh -b -p "$CONDA_INSTALL_PREFIX" $conda_install_extra
    rm ./install_conda.sh

    # see https://conda-forge.org/docs/user/tipsandtricks.html#multiple-channels
    # for more information on strict channel_priority
    "${DRY_RUN_ECHO[@]}" $SUDO "$CONDA_EXE" config --system --set channel_priority strict
    # By default, don't mess with people's PS1, I personally find it annoying
    "${DRY_RUN_ECHO[@]}" $SUDO "$CONDA_EXE" config --system --set changeps1 false
    # don't automatically activate the 'base' environment when intializing shells
    "${DRY_RUN_ECHO[@]}" $SUDO "$CONDA_EXE" config --system --set auto_activate_base false
    # don't automatically update conda to avoid https://github.com/conda-forge/conda-libmamba-solver-feedstock/issues/2
    "${DRY_RUN_ECHO[@]}" $SUDO "$CONDA_EXE" config --system --set auto_update_conda false

    # conda-build is a special case and must always be installed into the base environment
    $SUDO "$CONDA_EXE" install $DRY_RUN_OPTION -y -n base conda-build

    # conda-libmamba-solver is a special case and must always be installed into the base environment
    # see https://www.anaconda.com/blog/a-faster-conda-for-a-growing-community
    $SUDO "$CONDA_EXE" install $DRY_RUN_OPTION -y -n base conda-libmamba-solver

    # conda-lock is a special case and must always be installed into the base environment
    $SUDO "$CONDA_EXE" install $DRY_RUN_OPTION -y -n base conda-lock

    # Use the fast solver by default
    "${DRY_RUN_ECHO[@]}" $SUDO "$CONDA_EXE" config --system --set experimental_solver libmamba

    conda_init_extra_args=()
    if [[ "$INSTALL_TYPE" == system ]]; then
        # if we're installing into a root-owned directory using sudo, or we're already root
        # initialize conda in the system-wide rcfiles
        conda_init_extra_args=(--no-user --system)
    fi
    $SUDO "${CONDA_EXE}" init $DRY_RUN_OPTION "${conda_init_extra_args[@]}" bash

    if [[ $REINSTALL_CONDA -eq 1 ]]; then
        echo "::INFO:: Done reinstalling conda. Exiting"
        exit 0
    fi
fi
