#!/bin/bash

set -ex

git clone https://github.com/ucb-bar/esp-isa-sim.git
cd esp-isa-sim
git checkout 090e82c473fd28b4eb2011ffcd771ead6076faab

cd build
mkdir /home/centos/spike-local
../configure --prefix=/home/centos/spike-local --without-boost
make –j16
make install

export PATH=/home/centos/spike-local:$PATH

echo "Success! SHA3 Spike Built + Added to PATH"
