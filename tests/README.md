# Chipyard Tests

To build the tests, we do the following procedure.

## Clean the previous build

First, we clean the previous build. This step is recommended when any changes are made to the CMake build flow. This is equivalent to running `make clean` to the CMake build system.

```bash
cd $chipyard/tests/
rm -rf ./build/
```


## Configure the CMake build system

Then, we configure the cmake to build with the following settings. This step only needs to be done once when the build directory is not created.

```bash
cmake -S ./ -B ./build/ -D CMAKE_BUILD_TYPE=Debug
```

`-S` specifies the source directory, which is the current directory.

`-B` specifies the build directory, which is `./build/`.

`-D CMAKE_BUILD_TYPE=Debug` specifies the build type, which is debug.


## Build the tests

Then, we build the tests with the following command.

By default, the target is `all`, which builds all the tests.

```bash
cmake --build ./build/ --target all
```

If only specific tests are needed, we can specify the tests by adding the test name after `--target`.

For example, to build the `hello` test, we can run the following command.

```bash
cmake --build ./build/ --target hello
``` 


## Generating disassembly of the tests

To dump the disassembly of the tests, we can run the following command.

```bash
cmake --build ./build/ --target dump
``` 

To dump the disassembly of the `hello` test, we can run the following command.

```bash
cmake --build ./build/ --target hello_dump
``` 

## Clean the previous build

To clean the previous build, we can run the following command.

```bash
cmake --build ./build/ --target clean
``` 