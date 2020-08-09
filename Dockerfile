FROM archlinux

# This Dockerfile was created for the Tendermint Hardware project and funded by Tendermint, Inc.
# It is focused on speed and ease of use, so it uses pre-compiled tools availalbe in Arch Linux.
# This Dockerfile can be used in CI systems like the one found here: https://gitlab.com/virgohardware/core
# Individual developers can also use this Dockerfile to experiment with Chipyard without having to worry about configuring a development environment.
MAINTAINER jacobgadikian@gmail.com

# Every risc-v package in community
RUN pacman -Syyu --noconfirm python-pythondata-cpu-vexriscv riscv32-elf-binutils riscv32-elf-gdb riscv32-elf-newlib riscv64-elf-binutils riscv64-elf-gcc riscv64-elf-gdb riscv64-elf-newlib riscv64-linux-gnu-binutils riscv64-linux-gnu-gcc riscv64-linux-gnu-gdb riscv64-linux-gnu-glibc riscv64-linux-gnu-linux-api-headers

# Declare location of RISC-V tools
ENV RISCV=/usr/bin

# Chipyard dependencies recommended by docs: https://chipyard.readthedocs.io/en/latest/Chipyard-Basics/Initial-Repo-Setup.html
RUN pacman -Syyu --noconfirm base-devel bison flex gmp mpfr mpc zlib vim sbt scala texinfo gengetopt expat libusb ncurses cmake python patch diffstat texi2html texinfo subversion chrpath git wget gtk3 dtc rsync libguestfs expat ctags verilator jre11-openjdk spike

RUN archlinux-java set java-11-openjdk

# Create builduser
#RUN pacman -S --needed --noconfirm sudo && \
#        useradd builduser -m && passwd -d builduser && \
#         printf 'builduser ALL=(ALL) ALL\n' | tee -a /etc/sudoers

# Install RISCV-fesvr
# Build and install RISCV-FESVR
# RUN sudo -u builduser bash -c 'cd ~ && git clone https://aur.archlinux.org/riscv-fesvr-git.git && cd riscv-fesvr-git && makepkg -si --noconfirm && cd .. && rm -rf riscv-fesvr-git'

# Install Chipyard
RUN git clone https://github.com/ucb-bar/chipyard.git && \
        cd chipyard && \
        ./scripts/init-submodules-no-riscv-tools.sh

# Does this have something to do with Arch putting RISCV tools in /usr/bin?
# Symlink libfesvr.a (seems compiler can't find it otherwise)
RUN mkdir /usr/bin/lib && \
        ln -s /usr/lib/libfesvr.a /usr/bin/lib/libfesvr.a


