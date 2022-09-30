#!/bin/bash

set -x

echo "making tutorial directory"
mkdir -p ~/tutorial-installs
cd ~/tutorial-installs
export TUTORIAL_INSTALL_PATH=$(pwd)
export PATH=$TUTORIAL_INSTALL_PATH/bin:$PATH


# KLayout
echo "installing klayout"
cd $TUTORIAL_INSTALL_PATH
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get -y install klayout
klayout -h
klayout -v
which klayout
# wget -q https://www.klayout.org/downloads/source/klayout-0.27.1.tar.gz
# tar zxf klayout-0.27.1.tar.gz
# cd klayout-0.27.1
# ./build.sh -without-qtbinding
# ls

# >>>>>>>>> THIS WORKS
# Sky130 PDK
echo "installing sky130A PDK"
wget -q https://github.com/nayiri-k/hammer-workspace/raw/main/tech/sky130A.tar.bz2
tar -xf sky130A.tar.bz2

# Sky130 SRAMs
echo "installing sky130 sram macros"
git clone https://github.com/efabless/sky130_sram_macros.git

# Yosys
echo "installing yosys"
wget -q https://github.com/YosysHQ/oss-cad-suite-build/releases/download/2022-09-29/oss-cad-suite-linux-x64-20220929.tgz
tar zxf oss-cad-suite-linux-x64-20220929.tgz
export PATH=$TUTORIAL_INSTALL_PATH/oss-cad-suite/bin:$PATH
yosys -help
yosys -version
which yosys

# OpenROAD
# first install dependencies
echo "installing openroad dependencies"
git clone --recursive https://github.com/The-OpenROAD-Project/OpenROAD.git
cd OpenROAD
sudo ./etc/DependencyInstaller.sh -dev

echo "installing openroad"
mkdir build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$TUTORIAL_INSTALL_PATH 
make
make install

openroad -help
openroad -version
which openroad


yosys -help
openroad -help
klayout -h

yosys -version
openroad -version
klayout -v

which yosys
which openroad
which klayout

echo "End of tutorial setup testing"
# <<<<<<<<< THIS WORKS





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
