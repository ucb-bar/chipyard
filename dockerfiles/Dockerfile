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
ENV RISCV="/root/riscv-tools-install"
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

# update gemmini's onnxruntime
RUN cd chipyard && \
	 git -C generators/gemmini/ submodule update --init --recursive software/onnxruntime-riscv

# TODO UPDATE TO USE COMMIT WITH PEP8 FIX
RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv && \
	git checkout 2021-05-12 && \
	git submodule update --init --recursive

RUN pip3 install flake8

# Update PATH for RISCV toolchain (note: hardcoded for CircleCI)
ENV RISCV="/root/chipyard/esp-tools-install"
ENV LD_LIBRARY_PATH="$RISCV/lib"
ENV PATH="$RISCV/bin:$PATH"

RUN ls $RISCV

RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv && \
	./build.sh --config=Release --parallel --enable_training


RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv/systolic_runner/imagenet_runner && \
	./build.sh --config=Release --parallel --enable_training

RUN cd chipyard/generators/gemmini/software/onnxruntime-riscv/systolic_runner/imagenet_trainer && \
	./build.sh --config=Release --parallel --enable_training


# Set up FireMarshal. Building and cleaning br-base.json builds the underlying
# buildroot image (which takes a long time) but doesn't keep all the br-base
# stuff around (since that's faster to rebuild).
#RUN cd chipyard && \
#        source env.sh && \
#        cd software/firemarshal && \
#        ./init-submodules.sh && \
#        pip3 install -r python-requirements.txt && \
#        marshal build br-base.json && \
#        marshal clean br-base.json
        

# Run script to set environment variables on entry
ENTRYPOINT ["chipyard/scripts/entrypoint.sh"]

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
