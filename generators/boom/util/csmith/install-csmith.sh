#!/bin/bash

set -ex

# checkout csmith
if [ ! -d csmith ]; then
  git clone https://github.com/csmith-project/csmith.git
fi
cd csmith
git checkout deddca60d146c692e0ec5e4e345c466bbb3594b1

# install dependencies
#sudo apt-get update -y
#sudo apt-get install -y m4 cmake

# build csmith
cmake -DCMAKE_INSTALL_PREFIX=$RISCV .
make -j8 && make install
