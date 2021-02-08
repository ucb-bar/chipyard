### This is a full chipyard setup

# BUILD BASE FOR CI

FROM ubuntu:18.04 as base
ARG CHIPYARD_HASH

MAINTAINER https://groups.google.com/forum/#!forum/chipyard

# Install dependencies for ubuntu-req.sh
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y \
               curl \
               git \
               sudo

# Stopping docker keyboard-config from disrupting ubuntu-req.sh
RUN sudo DEBIAN_FRONTEND=noninteractive apt-get install -y keyboard-configuration && \
   sudo DEBIAN_FRONTEND=noninteractive apt-get install -y console-setup

# Adds a new user called riscvuser
RUN groupadd --gid 3434 riscvuser \
    && useradd --uid 3434 --gid riscvuser --shell /bin/bash --create-home riscvuser \
    && echo 'riscvuser ALL=NOPASSWD: ALL' >> /etc/sudoers.d/50-riscvuser \
    && echo 'Defaults    env_keep += "DEBIAN_FRONTEND"' >> /etc/sudoers.d/env_keep

WORKDIR /home/riscvuser
USER riscvuser

# Update PATH for RISCV toolchain (note: hardcoded for CircleCI)
ENV RISCV="/home/riscvuser/riscv-tools-install"
ENV LD_LIBRARY_PATH="$RISCV/lib"
ENV PATH="$RISCV/bin:$PATH"

# Install Chipyard and run ubuntu-req.sh to install necessary dependencies
RUN git clone https://github.com/ucb-bar/chipyard.git && \
        cd chipyard && \
        git checkout $CHIPYARD_HASH && \
        ./scripts/ubuntu-req.sh 1>/dev/null


# BUILD IMAGE WITH TOOLCHAINS

# Use above build as base
FROM base as base-with-tools

# Init submodules
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/init-submodules-no-riscv-tools.sh 1>/dev/null

# Install riscv-tools
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/build-toolchains.sh riscv-tools 1>/dev/null

# Install esp-tools
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/build-toolchains.sh esp-tools 1>/dev/null

# Run script to set environment variables on entry
ENTRYPOINT ["chipyard/scripts/entrypoint.sh"]

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
