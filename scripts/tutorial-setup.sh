#!/bin/bash

mkdir -p vlsi/tutorial-installs
cd vlsi/tutorial
export TUTORIAL_INSTALL_PATH=$(pwd)
# Sky130 PDK
wget https://github.com/nayiri-k/hammer-workspace/raw/main/tech/sky130A.tar.bz2
tar -xvf sky130A.tar.bz2
# Sky130 SRAMs
git clone git@github.com:efabless/sky130_sram_macros.git
# Yosys
# conda create --name yosys --no-default-packages -y
conda install -c timvideos -y yosys
yosys -help
# OpenROAD
# first install dependencies
conda install -y -c anaconda libffi
conda install -y -c intel tcl
conda install -y -c conda-forge time
conda install -y -c anaconda pandas
# build openroad
git clone --recursive https://github.com/The-OpenROAD-Project/OpenROAD.git
cd OpenROAD
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$TUTORIAL_INSTALL_PATH
make
make install
export PATH=$TUTORIAL_INSTALL_PATH/bin:$PATH
openroad -help
# KLayout
cd $TUTORIAL_INSTALL_PATH
wget https://www.klayout.org/downloads/source/klayout-0.27.1.tar.gz
tar zxvf klayout-0.27.1.tar.gz
cd klayout-0.27.1
./build.sh -without-qtbinding
ls

set -ex

RDIR=$(git rev-parse --show-toplevel)

cd $RDIR

git rm generators/chipyard/src/main/scala/config/RocketSha3Configs.scala
git rm -rf generators/sha3

for p in scripts/tutorial-patches/*.patch
do
    echo "Applying tutorial patch $p"
    git apply $p
done
