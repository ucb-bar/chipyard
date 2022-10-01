#!/bin/bash

pushd /home/centos
git clone https://github.com/ucb-bar/esp-isa-sim.git
pushd esp-isa-sim
git checkout 090e82c473fd28b4eb2011ffcd771ead6076faab

mkdir -p build
pushd build
mkdir /home/centos/spike-local
../configure --prefix=/home/centos/spike-local --without-boost
make -j16 default
make -j16 install

export PATH=/home/centos/spike-local/bin:$PATH

popd
popd
popd

echo "Success! SHA3 Spike Built + Added to PATH"
