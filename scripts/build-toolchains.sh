#!/usr/bin/env bash

#this script is based on the firesim build toolchains script

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(pwd)
CHIPYARD_DIR="${CHIPYARD_DIR:-$(git rev-parse --show-toplevel)}"

usage() {
    echo "usage: ${0} [riscv-tools | esp-tools | ec2fast]"
    echo "   riscv: if set, builds the riscv toolchain (this is also the default)"
    echo "   hwacha: if set, builds esp-tools toolchain"
    echo "   ec2fast: if set, pulls in a pre-compiled RISC-V toolchain for an EC2 manager instance"
    exit "$1"
}

error() {
    echo "${0##*/}: ${1}" >&2
}
die() {
    error "$1"
    exit "${2:--1}"
}

TOOLCHAIN="riscv-tools"
EC2FASTINSTALL="false"

while getopts 'hH-:' opt ; do
    case $opt in
    h|H)
        usage 3 ;;
    -)
        case $OPTARG in
        help)
            usage 3 ;;
        ec2fast) # Preserve compatibility
            EC2FASTINSTALL=true ;;
        *)
            error "invalid option: --${OPTARG}"
            usage 1 ;;
        esac ;;
    *)
        error "invalid option: -${opt}"
        usage 1 ;;
    esac
done

shift $((OPTIND - 1))

if [ "$1" = ec2fast ] ; then
    EC2FASTINSTALL=true
elif [ -n "$1" ] ; then
    TOOLCHAIN="$1"
fi

INSTALL_DIR="$TOOLCHAIN-install"

RISCV="$(pwd)/$INSTALL_DIR"

# install risc-v tools
export RISCV="$RISCV"

cd "${CHIPYARD_DIR}"

SRCDIR="$(pwd)/toolchains/${TOOLCHAIN}"
[ -d "${SRCDIR}" ] || die "unsupported toolchain: ${TOOLCHAIN}"
. ./scripts/build-util.sh


if [ "${EC2FASTINSTALL}" = true ] ; then
    [ "${TOOLCHAIN}" = 'riscv-tools' ] ||
        die "unsupported precompiled toolchain: ${TOOLCHAIN}"

    echo '=>  Fetching pre-built toolchain'
    module=toolchains/riscv-tools/riscv-gnu-toolchain-prebuilt
    git config --unset submodule."${module}".update || :
    git submodule update --init --depth 1 "${module}"

    echo '==>  Verifying toolchain version hash'
    # Find commit hash without initializing the submodule
    hashsrc="$(git ls-tree -d HEAD "${SRCDIR}/riscv-gnu-toolchain" | {
        unset IFS && read -r _ type obj _ &&
        test -n "${obj}" && test "${type}" = 'commit' && echo "${obj}"
    }; )" ||
        die 'failed to obtain riscv-gnu-toolchain submodule hash' "$?"

    read -r hashbin < "${module}/HASH" ||
        die 'failed to obtain riscv-gnu-toolchain-prebuilt hash' "$?"

    echo "==>  ${hashsrc}"
    [ "${hashsrc}" = "${hashbin}" ] ||
        die "pre-built version mismatch: ${hashbin}"

    echo '==>  Installing pre-built toolchain'
    "${MAKE}" -C "${module}" DESTDIR="${RISCV}" install
    git submodule deinit "${module}" || :

else
    module_prepare riscv-gnu-toolchain qemu
    module_build riscv-gnu-toolchain --prefix="${RISCV}"
    echo '==>  Building GNU/Linux toolchain'
    module_make riscv-gnu-toolchain linux
fi

module_all riscv-isa-sim --prefix="${RISCV}"
# build static libfesvr library for linking into firesim driver (or others)
echo '==>  Installing libfesvr static library'
module_make riscv-isa-sim libfesvr.a
cp -p "${SRCDIR}/riscv-isa-sim/build/libfesvr.a" "${RISCV}/lib/"

CC= CXX= module_all riscv-pk --prefix="${RISCV}" --host=riscv64-unknown-elf
module_all riscv-tests --prefix="${RISCV}/riscv64-unknown-elf"

cd "$RDIR"

{
    echo "export CHIPYARD_TOOLCHAIN_SOURCED=1"
    echo "export RISCV=$(printf '%q' "$RISCV")"
    echo "export PATH=\${RISCV}/bin:\${PATH}"
    echo "export LD_LIBRARY_PATH=\${RISCV}/lib\${LD_LIBRARY_PATH:+":\${LD_LIBRARY_PATH}"}"
} > env.sh
echo "Toolchain Build Complete!"
