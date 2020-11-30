### Note: This DockerFile is adapted from https://github.com/ucb-bar/chipyard/blob/master/.circleci/images/Dockerfile which was adapted from: https://github.com/CircleCI-Public/example-images/openjdk
# This is a full chipyard setup, which will be built manually on-demand in the Tendermint Hardware Project at https://gitlab.com/virgohardware/core/

FROM ubuntu:18.04

MAINTAINER jacobgadikian@gmail.com

# man directory is missing in some base images
# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y \
               git \
               sudo

# Stopping docker keyboard-config from disrupting ubuntu-req.sh
RUN sudo DEBIAN_FRONTEND=noninteractive apt-get install -y keyboard-configuration && \
   sudo DEBIAN_FRONTEND=noninteractive apt-get install -y console-setup

RUN groupadd --gid 3434 riscvuser \
    && useradd --uid 3434 --gid riscvuser --shell /bin/bash --create-home riscvuser \
    && echo 'riscvuser ALL=NOPASSWD: ALL' >> /etc/sudoers.d/50-riscvuser \
    && echo 'Defaults    env_keep += "DEBIAN_FRONTEND"' >> /etc/sudoers.d/env_keep

WORKDIR /home/riscvuser
USER riscvuser

# Install Chipyard
RUN git clone https://github.com/ucb-bar/chipyard.git && \
        cd chipyard && \
        ./scripts/ubuntu-req.sh 1>/dev/null

# Install dependencies from ubuntu-req.sh
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

ENTRYPOINT ["/home/riscvuser/chipyard/scripts/entrypoint.sh"]

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
