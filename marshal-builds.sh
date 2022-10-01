#!/bin/bash

pushd /home/centos/chipyard-afternoon/generators/sha3/software/
marshal -v build br-base.json
marshal -v build marshal-configs/sha3-linux.yaml
marshal -v build marshal-configs/sha3-linux-jtr-crack.yaml
marshal -v build marshal-configs/sha3-linux-jtr-test.yaml
marshal -v install marshal-configs/sha3*.yaml
popd
