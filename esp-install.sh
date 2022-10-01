#!/bin/bash

set -ex

pushd /home/centos
git clone https://github.com/ucb-bar/esp-isa-sim.git
pushd esp-isa-sim
git checkout 090e82c473fd28b4eb2011ffcd771ead6076faab

mkdir -p build
pushd build
mkdir /home/centos/spike-local
../configure --prefix=/home/centos/spike-local --without-boost
make default
make install

export PATH=/home/centos/spike-local:$PATH

popd
popd
popd

echo "Success! SHA3 Spike Built + Added to PATH"
