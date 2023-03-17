#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)

# get helpful utilities
source $RDIR/scripts/utils.sh

common_setup

# Allow user to override MAKE
[ -n "${MAKE:+x}" ] || MAKE=$(command -v gnumake || command -v gmake || command -v make)
readonly MAKE

usage() {
    echo "usage: ${0} [OPTIONS] [riscv-tools | esp-tools]"
    echo ""
    echo "Installation Types"
    echo "   riscv-tools: if set, builds the riscv toolchain (this is also the default)"
    echo "   esp-tools: if set, builds esp-tools toolchain used for the hwacha vector accelerator"
    echo ""
    echo "Options"
    echo "   --prefix -p PREFIX    : Install destination."
    echo "   --clean-after-install : Run make clean in calls to module_make and module_build"
    echo "   --help -h             : Display this message"
    exit "$1"
}

TOOLCHAIN="riscv-tools"
CLEANAFTERINSTALL=""
RISCV=""
FORCE=false

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | -H | --help | help )
            usage 3 ;;
        -p | --prefix )
            shift
            RISCV=$(realpath $1) ;;
        --clean-after-install )
            CLEANAFTERINSTALL="true" ;;
        riscv-tools | esp-tools)
            TOOLCHAIN=$1 ;;
        * )
            error "invalid option $1"
            usage 1 ;;
    esac
    shift
done

if [ -z "$RISCV" ] ; then
    error "ERROR: Prefix not given. If conda is sourced, do you mean $CONDA_PREFIX/$TOOLCHAIN?"
fi

XLEN=64

echo "Installing extra toolchain utilities/tests to $RISCV"

# install risc-v tools
export RISCV="$RISCV"

cd "${RDIR}"

SRCDIR="$(pwd)/toolchains/${TOOLCHAIN}"
[ -d "${SRCDIR}" ] || die "unsupported toolchain: ${TOOLCHAIN}"
. ./scripts/build-util.sh

echo '==>  Installing Spike'
# disable boost explicitly for https://github.com/riscv-software-src/riscv-isa-sim/issues/834
# since we don't have it in our requirements
module_all riscv-isa-sim --prefix="${RISCV}" --with-boost=no --with-boost-asio=no --with-boost-regex=no
# build static libfesvr library for linking into firesim driver (or others)
echo '==>  Installing libfesvr static library'
OLDCLEANAFTERINSTALL=$CLEANAFTERINSTALL
CLEANAFTERINSTALL=""
module_make riscv-isa-sim libfesvr.a
cp -p "${SRCDIR}/riscv-isa-sim/build/libfesvr.a" "${RISCV}/lib/"
CLEANAFTERINSTALL=$OLDCLEANAFTERINSTALL

echo '==>  Installing Proxy Kernel'
CC= CXX= module_all riscv-pk --prefix="${RISCV}" --host=riscv${XLEN}-unknown-elf

echo '==>  Installing RISC-V tests'
module_all riscv-tests --prefix="${RISCV}/riscv${XLEN}-unknown-elf" --with-xlen=${XLEN}

echo '==> Installing espresso logic minimizer'
(
    cd $RDIR
    git submodule update --init --checkout generators/constellation
    cd generators/constellation
    scripts/install-espresso.sh $RISCV
)

# Common tools (not in any particular toolchain dir)

echo '==>  Installing libgloss'
CC= CXX= SRCDIR="$(pwd)/toolchains" module_all libgloss --prefix="${RISCV}/riscv${XLEN}-unknown-elf" --host=riscv${XLEN}-unknown-elf

cd $RDIR
if [ $TOOLCHAIN == "riscv-tools" ]; then
    echo '==> Installing gemmini spike extensions'
    git submodule update --init generators/gemmini
    cd generators/gemmini
    git submodule update --init software/libgemmini
    make -C $RDIR/generators/gemmini/software/libgemmini install
fi

echo '==>  Installing DRAMSim2 Shared Library'
cd $RDIR
git submodule update --init tools/DRAMSim2
cd tools/DRAMSim2
make libdramsim.so
cp libdramsim.so $RISCV/lib/

echo "Extra Toolchain Utilities/Tests Build Complete!"
