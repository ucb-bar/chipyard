#!/bin/bash
set -ex
echo "making tutorial directory"
rm -rf /home/centos/tutorial-installs
mkdir -p /home/centos/tutorial-installs
cd /home/centos/tutorial-installs
export TUTORIAL_INSTALL_PATH=$(pwd)
export PATH=$TUTORIAL_INSTALL_PATH/bin:$PATH
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

## NAYIRI SAID STOP HERE

# # OpenROAD
# # first install dependencies
# echo "installing openroad dependencies"
# rm -rf OpenROAD
# sudo rm -rf /tmp/installers
# git clone --recursive https://github.com/The-OpenROAD-Project/OpenROAD.git
# cd OpenROAD
# sudo PATH=$PATH ./etc/DependencyInstaller.sh -dev
# (
# source /opt/rh/devtoolset-8/enable
# echo "installing openroad"
# mkdir build
# cd build
# cmake .. -DCMAKE_INSTALL_PREFIX=$TUTORIAL_INSTALL_PATH
# make -j16
# make -j16 install
# )
# openroad -help
# openroad -version
# which openroad
# # KLayout
# echo "installing klayout"
# cd $TUTORIAL_INSTALL_PATH
# wget https://www.klayout.org/downloads/CentOS_7/klayout-0.27.1-0.x86_64.rpm
# sudo yum -y localinstall klayout-0.27.1-0.x86_64.rpm
# #sudo apt-get --yes --force-yes update
# #sudo DEBIAN_FRONTEND=noninteractive apt-get  --yes --force-yes install klayout
# which klayout
# yosys -help
# openroad -help
# yosys -version
# openroad -version
# which yosys
# which openroad
# which klayout
# echo "End of tutorial setup testing"
