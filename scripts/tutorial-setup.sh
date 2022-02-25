#!/bin/bash

set -e -x

git rm generators/chipyard/src/main/scala/configs/RocketSha3Configs.scala
git rm -rf generators/sha3

for p in scripts/tutorial-patches/*.patch
do
    echo "Applying tutorial patch $p"
    git apply $p
done
