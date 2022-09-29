#!/bin/bash

set -x

echo "making tutorial directory"
mkdir -p ~/tutorial-installs
cd ~/tutorial-installs
export TUTORIAL_INSTALL_PATH=$(pwd)
# # >>>>>>>>> THIS WORKS
# # Sky130 PDK
# echo "installing sky130A PDK"
# wget https://github.com/nayiri-k/hammer-workspace/raw/main/tech/sky130A.tar.bz2
# tar -xf sky130A.tar.bz2
# # <<<<<<<<< THIS WORKS
# Sky130 SRAMs
echo "installing sky130 sram macros"
git clone https://github.com/efabless/sky130_sram_macros.git
# Yosys
# conda create --name yosys --no-default-packages -y
echo "installing yosys"
# conda install -c timvideos -y yosys
wget https://github.com/YosysHQ/oss-cad-suite-build/releases/download/2022-09-29/oss-cad-suite-linux-x64-20220929.tgz
tar zxf oss-cad-suite-linux-x64-20220929.tgz
export PATH=$TUTORIAL_INSTALL_PATH/oss-cad-suite/bin:$PATH
yosys -help
# OpenROAD
# first install dependencies
echo "installing openroad dependencies"
conda install -y -c anaconda libffi
conda install -y -c intel tcl
conda install -y -c conda-forge time
conda install -y -c anaconda pandas
# build openroad
echo "installing openroad"
git clone --recursive https://github.com/The-OpenROAD-Project/OpenROAD.git
cd OpenROAD
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$TUTORIAL_INSTALL_PATH
make
make install
export PATH=$TUTORIAL_INSTALL_PATH/bin:$PATH
openroad -help
# KLayout
echo "installing klayout"
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
