#!/bin/sh

git submodule update --init
cd rocket-chip && git submodule update --init
cd ../testchipip && git submodule update --init
