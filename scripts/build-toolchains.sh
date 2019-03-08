#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

unamestr=$(uname)
RDIR=$(pwd)

if [ $# -ne 0 ]; then
  TOOLCHAIN=$1
  if [ $1 == "riscv" ]; then
    TOOLCHAIN="riscv-tools"
  elif [ $1 == "hwacha" ]; then
    TOOLCHAIN="esp-tools"
  fi
else
  TOOLCHAIN="riscv-tools"
fi

INSTALL_DIR="$TOOLCHAIN-install"
mkdir -p "$(pwd)/$INSTALL_DIR"

RISCV="$(pwd)/$INSTALL_DIR"

# install risc-v tools
export RISCV="$RISCV"
git submodule update --init --recursive toolchains/$TOOLCHAIN #--jobs 8
cd "toolchains/$TOOLCHAIN"
export MAKEFLAGS="-j16"
./build.sh
cd $RDIR

echo "export RISCV=$RISCV" > env.sh
echo "export PATH=$RISCV/bin:$RDIR/$DTCversion:\$PATH" >> env.sh
echo "export LD_LIBRARY_PATH=$RISCV/lib" >> env.sh
echo "Toolchain Build Complete!"
