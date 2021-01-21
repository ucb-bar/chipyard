### Note: This DockerFile is adapted from https://github.com/ucb-bar/chipyard/blob/master/.circleci/images/Dockerfile which was adapted from: https://github.com/CircleCI-Public/example-images/openjdk
# This is a full chipyard setup

FROM ubuntu:18.04
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

# Install Chipyard
RUN git clone https://github.com/ucb-bar/chipyard.git && \
        cd chipyard && \
        git checkout $CHIPYARD_HASH && \
        ./scripts/ubuntu-req.sh 1>/dev/null

# Run ubuntu-req.sh to install necessary dependencies
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

ENTRYPOINT ["chipyard/scripts/entrypoint.sh"]

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
