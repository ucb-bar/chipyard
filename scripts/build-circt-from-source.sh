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
    echo "usage: ${0}"
    echo ""
    echo "Options"
    echo "   --prefix -p PREFIX    : Install destination."
    echo "   --help -h             : Display this message"
    echo "   --no-conda            : Do not link CIRCT with conda libraries"
    exit "$1"
}

PREFIX=""
CONDA=1

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | -H | --help | help )
            usage 3 ;;
        -p | --prefix )
            shift
            PREFIX=$(realpath $1) ;;
        --no-conda )
            unset CONDA ;;
        * )
            error "invalid option $1"
            usage 1 ;;
    esac
    shift
done

if [ -z "$PREFIX" ] ; then
    error "ERROR: Prefix not given."
    exit 1
fi



echo "Cloning CIRCT"
(
    cd $RDIR/tools
    git submodule update --init --progress circt
)
echo "Cloning CIRCT/LLVM"
(
    cd $RDIR/tools/circt
    git submodule init
    # The settings in circt/.gitmodules don't "stick", so force-set them here
    git config submodule.llvm.shallow true
    git config submodule.llvm.branch main
    git submodule update --recommend-shallow --progress llvm
)

echo "Building CIRCT's LLVM/MLIR"
(
    cd $RDIR/tools/circt
    rm -rf llvm/build
    mkdir llvm/build
    cd llvm/build
    cmake -G Ninja ../llvm \
          -DLLVM_ENABLE_PROJECTS="mlir" \
          -DLLVM_TARGETS_TO_BUILD="host" \
          -DLLVM_ENABLE_ASSERTIONS=ON \
          -DCMAKE_BUILD_TYPE=RELEASE \
          -DCMAKE_EXPORT_COMPILE_COMMANDS=ON \
          ${CONDA:+-DCMAKE_EXE_LINKER_FLAGS="-L$RDIR/.conda-env/lib"}
    ninja
)

echo "Building CIRCT"
(
    cd $RDIR/tools/circt
    rm -rf build
    mkdir build
    cd build
    cmake -G Ninja .. \
          -DMLIR_DIR=$RDIR/tools/circt/llvm/build/lib/cmake/mlir \
          -DLLVM_DIR=$RDIR/tools/circt/llvm/build/lib/cmake/llvm \
          -DLLVM_ENABLE_ASSERTIONS=ON \
          -DCMAKE_BUILD_TYPE=RELEASE \
          -DCMAKE_INSTALL_PREFIX=$PREFIX \
          ${CONDA:+-DCMAKE_EXE_LINKER_FLAGS="-L$RDIR/.conda-env/lib"}
    ninja
)

echo "Installing CIRCT to $PREFIX"
(
    cd $RDIR/tools/circt/build
    ninja install
)

