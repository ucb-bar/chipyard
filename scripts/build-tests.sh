#!/usr/bin/env bash

cmake ./tests/ -S ./tests/ -B ./tests/build/ -D CMAKE_BUILD_TYPE=Debug
cmake --build ./tests/build/ --target all
