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
    echo "usage: ${0} [OPTIONS] [riscv-tools]"
    echo ""
    echo "Installation Types"
    echo "   riscv-tools: if set, builds the riscv toolchain (this is also the default)"
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
        riscv-tools )
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
CC= CXX= module_all riscv-pk --prefix="${RISCV}" --host=riscv${XLEN}-unknown-elf --with-arch=rv64gc_zifencei

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
(
    # Build libgloss with custom multilib configuration that includes _zicsr extension
    # This fixes CSR instruction compilation errors on newer toolchains where zicsr
    # is no longer implicitly included with base ISAs
    SRCDIR="$(pwd)/toolchains"
# git submodule update --init "${SRCDIR}/libgloss"
    cd "${SRCDIR}/libgloss"

    # Clean and prepare
    if [ -e build ] ; then
        rm -rf build
    fi
    mkdir -p build

    # Configure with explicit multilib settings including _zicsr extension
    cd build
    CC= CXX= ../configure \
        --prefix="${RISCV}/riscv${XLEN}-unknown-elf" \
        --host=riscv${XLEN}-unknown-elf \
        --enable-multilib="rv32i_zicsr/ilp32 rv32iac_zicsr/ilp32 rv32im_zicsr/ilp32 rv32imac_zicsr/ilp32 rv32imafc_zicsr/ilp32f rv64imac_zicsr/lp64 rv64imafdc_zicsr/lp64d"

    # Patch the generated Makefile to fix BSD install compatibility on macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # Replace GNU install flags (-D -t) with BSD-compatible version
        perl -i.bak -pe 's/(\tinstall -m 644 -p -D -t \$\$\(libdir\)\/\$\(1\)\/ \$\$\^)/\tmkdir -p \$\$(libdir)\/\$(1)\/ \&\& install -m 644 -p \$\$\^ \$\$(libdir)\/\$(1)\//' Makefile
    fi

    # Build and install
    "${MAKE}"
    "${MAKE}" install
) 2>&1 | tee toolchains/libgloss/build.log

# cd $RDIR
# if [ $TOOLCHAIN == "riscv-tools" ]; then
# echo '==> Installing gemmini spike extensions'
# git submodule update --init generators/gemmini
# cd generators/gemmini
# git submodule update --init software/libgemmini
# make -C $RDIR/generators/gemmini/software/libgemmini install
# fi

echo '==>  Installing DRAMSim2 Shared Library'
cd $RDIR/tools/DRAMSim2
make clean
make libdramsim.so
cp libdramsim.so $RDIR/sims/verilator

# echo '==>  Installing uart_tsi bringup utility'
# cd $RDIR
# git submodule update --init generators/testchipip
# cd generators/testchipip/uart_tsi
# make
# cp uart_tsi $RISCV/bin

echo '==>  Installing spike-devices'
cd $RDIR
git submodule update --init toolchains/riscv-tools/riscv-spike-devices
cd toolchains/riscv-tools/riscv-spike-devices
make install

echo "Extra Toolchain Utilities/Tests Build Complete!"
