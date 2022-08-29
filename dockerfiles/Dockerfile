### This is a full chipyard setup

# BUILD BASE FOR CI

FROM ubuntu:18.04 as base
#FROM ubuntu:20.04 as base
ARG CHIPYARD_HASH

MAINTAINER https://groups.google.com/forum/#!forum/chipyard

SHELL ["/bin/bash", "-c"] 

RUN ln -snf /usr/share/zoneinfo/$CONTAINER_TIMEZONE /etc/localtime && echo $CONTAINER_TIMEZONE > /etc/timezone

# Install dependencies for ubuntu-req.sh
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
               curl \
               git \
               sudo \
               ca-certificates \
               keyboard-configuration \
               console-setup \
               bc \
               unzip \
	       build-essential \
	       openssl \
	       libssl-dev

WORKDIR /root

# Install latest version of CMake
RUN git clone https://github.com/Kitware/CMake/ && \
	cd CMake && \
	./bootstrap && make && make install 


# Install Chipyard and run ubuntu-req.sh to install necessary dependencies
RUN 	git clone https://github.com/ucb-bar/chipyard.git && \
        cd chipyard && \
        git checkout $CHIPYARD_HASH && \
        ./scripts/ubuntu-req.sh && \
        sudo rm -rf /var/lib/apt/lists/*

# Update PATH for RISCV toolchain (note: hardcoded for CircleCI)
ENV RISCV="/root/chipyard/esp-tools-install"
ENV LD_LIBRARY_PATH="$RISCV/lib"
ENV PATH="$RISCV/bin:$PATH"

# BUILD IMAGE WITH TOOLCHAINS

# Use above build as base
FROM base as base-with-tools

# Init submodules
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/init-submodules-no-riscv-tools.sh 1>/dev/null

# Install riscv-tools
#RUN cd chipyard && \
#        export MAKEFLAGS=-"j $(nproc)" && \
#        ./scripts/build-toolchains.sh riscv-tools 1>/dev/null

# Install esp-tools
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/build-toolchains.sh esp-tools 1>/dev/null

# source environment variable
RUN cd chipyard && \
	source env.sh

FROM base-with-tools as gemmini


# Set up Gemmini
RUN cd chipyard/generators/gemmini && \
	git config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*" && \
	git fetch --depth=2147483647 && git checkout mlsys-tutorial-2022 && git pull origin dev && \
	git submodule update

RUN cd chipyard/toolchains/esp-tools/riscv-isa-sim/build && \
	git fetch --depth=2147483647 && git checkout 051d820f08be84d069993de4375d29c91eb2f577 && \
	make && make install

# Patch pk to work with onnxruntime
RUN cd chipyard/toolchains/esp-tools/riscv-pk && \
	git remote add mlsys https://github.com/hngenc/riscv-pk.git && \
	git fetch mlsys && \
	git checkout ort_pk_patch && \
	mkdir -p build && \
	cd build && \
	../configure --prefix=$RISCV --host=riscv64-unknown-elf && \
	make && \
	make install 


RUN pip3 install flake8

# make sure onnx runtime is on MLSys tutorial branch
RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv && \
	git checkout mlsys-tutorial-2022

# update gemmini's onnxruntime
RUN cd chipyard && \
	 git -C generators/gemmini/ submodule update --init --recursive software/onnxruntime-riscv


# RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv && \
# 	git checkout 2e6c3bb6a4b5ad49703a4d24b6153070f256c1b8 && \
# 	git submodule update --init --recursive

# Update PATH for RISCV toolchain (note: hardcoded for CircleCI)
ENV RISCV="/root/chipyard/esp-tools-install"
ENV LD_LIBRARY_PATH="$RISCV/lib"
ENV PATH="$RISCV/bin:$PATH"

RUN ls $RISCV

# MIDAS simulation
# RUN cd chipyard/sims/firesim && \
# 	git fetch --tags && git checkout 1.13.6 && \
# 	./build-setup.sh --library --skip-validate

# ONNX Runtime Set Up
# to replace with running the scripts


# RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv && \
# 	./build.sh --config=Release --parallel --enable_training

# RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv/systolic_runner/imagenet_runner && \
# 	./build.sh --config=Debug --parallel --enable_training --cmake_extra_defines onnxruntime_USE_SYSTOLIC=ON onnxruntime_SYSTOLIC_INT8=ON onnxruntime_SYSTOLIC_FP32=OFF

# RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv/systolic_runner/imagenet_trainer && \
# 	./build.sh --config=Debug --parallel --enable_training --cmake_extra_defines onnxruntime_USE_SYSTOLIC=ON onnxruntime_SYSTOLIC_INT8=OFF onnxruntime_SYSTOLIC_FP32=ON
        
# Run script to set environment variables on entry
ENTRYPOINT ["chipyard/scripts/entrypoint.sh"]

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
