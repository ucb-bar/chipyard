#!/bin/bash

set -ex

CYDIR=$(git rev-parse --show-toplevel)
G_DIR=$CYDIR/generators/gemmini/software/gemmini-rocc-tests
O_DIR=$CYDIR/software/tutorial/overlay/root

echo "Building Gemmini RoCC tests"
cd $G_DIR

./build.sh imagenet
cd build
rm -rf $O_DIR
mkdir -p $O_DIR
cp -r imagenet/resnet50-baremetal $O_DIR/
cp -r imagenet/resnet50-linux $O_DIR/
cp -r imagenet/mobilenet-baremetal $O_DIR/
cp -r imagenet/mobilenet-linux $O_DIR/

echo "Complete!"
