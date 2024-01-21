#!/bin/bash

set -ex

brew install cmake
brew install ninja
brew install coreutils
brew tap riscv-software-src/riscv
brew install riscv-tools
brew install shell
brew install openjdk@17
brew install jq
brew install make
brew install gnu-sed
brew install fd
brew install flex
brew install bison@3.8

cd $HOMEBREW_PREFIX
ln -s gmake make
ln -s gsed sed
export PATH=$(pwd):$PATH


export BASEDIR=cs252-labdir
mkdir $BASEDIR && cd $BASEDIR

echo "Cloning llvm circt"
git clone git@github.com:llvm/circt.git
cd circt
git checkout firtool-1.60.0

export CIRCT_BASE_DIR=$(pwd)
git submodule init
git submodule update

echo "Building LLVM"
cd llvm
git fetch --unshallow
mkdir build && cd build
cmake -G Ninja ../llvm \
    -DLLVM_ENABLE_PROJECTS="mlir" \
    -DLLVM_TARGETS_TO_BUILD="host" \
    -DLLVM_ENABLE_ASSERTIONS=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
ninja
ninja check-mlir

echo "Building firtool"
cd $CIRCT_BASE_DIR
mkdir build && cd build
cmake -G Ninja .. \
    -DMLIR_DIR=$PWD/../llvm/build/lib/cmake/mlir \
    -DLLVM_DIR=$PWD/../llvm/build/lib/cmake/llvm \
    -DLLVM_ENABLE_ASSERTIONS=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
ninja
ninja check-circt
export PATH=$(pwd)/bin:$PATH


cd $BASEDIR
git clone https://github.com/verilator/verilator.git
cd verilator
autoconf
./configure
make -j$(nproc)
export VERILATOR_ROOT=$(pwd)


cd $BASEDIR
git clone https://github.com/ucb-bar/chipyard.git
cd chipyard
export CHIPYARD_BASE_DIR=$(pwd)
mkdir .env && cd .env
export RISCV=$(pwd)
cd ..
./build-setup --skip-validate -s 1 -s 3 -s 4 -s 5 -s 6 -s 7 -s 8 -s 9 -s 10

git submodule update
cd $CHIPYARD_BASE_DIR/toolchains/riscv-tools/

cd riscv-isa-sim
mkdir build && cd build
../configure --prefix=$RISCV --with-boost=no --with-boost-asio=no --with-boost-regex=no
make -j$(nproc)
make install

cd ../../riscv-pk
mkdir build && cd build
../configure --prefix=$RISCV --host=riscv64-unknown-elf --with-arch=rv64imafdc_zifencei --with-abi=lp64
make -j$(nproc)
make install
