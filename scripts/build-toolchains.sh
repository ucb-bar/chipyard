#!/usr/bin/env bash

#this script is based on the firesim build toolchains script

# exit script if any command fails
set -e
set -o pipefail

unamestr=$(uname)
RDIR=$(pwd)
: ${CHIPYARD_DIR:=$(pwd)} #default value is the PWD unless overridden

function usage
{
    echo "usage: ./scripts/build-toolchains.sh [riscv] [hwacha] [ ec2fast | --ec2fast] "
    echo "   riscv: if set, builds the riscv toolchain (this is also the default)"
    echo "   hwacha: if set, builds esp-tools toolchain"
    echo "   ec2fast: if set, pulls in a pre-compiled RISC-V toolchain for an EC2 manager instance"
}

#taken from riscv-tools to check for open-ocd autoconf versions
check_version() {
    $1 --version | awk "NR==1 {if (\$NF>$2) {exit 0} exit 1}" || (
        echo $3 requires at least version $2 of $1. Aborting.
        exit 1
    )
}

if [ "$1" == "--help" -o "$1" == "-h" -o "$1" == "-H" ]; then
    usage
    exit 3
fi

TOOLCHAIN="riscv-tools"
EC2FASTINSTALL="false"
FASTINSTALL="false"
while test $# -gt 0
do
   case "$1" in
        riscv)
            TOOLCHAIN="riscv-tools"
            ;;
        hwacha)
            TOOLCHAIN="esp-tools"
            ;;
        ec2fast | --ec2fast) # I don't want to break this api
            EC2FASTINSTALL=true
            ;;
        -h | -H | --help)
            usage
            exit
            ;;
        --*) echo "ERROR: bad option $1"
            usage
            exit 1
            ;;
        *) echo "ERROR: bad argument $1"
            usage
            exit 2
            ;;
    esac
    shift
done


if [ "$EC2FASTINSTALL" = "true" ]; then
    if [ "$TOOLCHAIN" = "riscv-tools" ]; then
      cd $RDIR
      git clone https://github.com/firesim/firesim-riscv-tools-prebuilt.git
      cd firesim-riscv-tools-prebuilt
      git checkout 56a40961c98db5e8f904f15dc6efd0870bfefd9e
      PREBUILTHASH="$(cat HASH)"
      git -C $CHIPYARD_DIR submodule update --init  toolchains/$TOOLCHAIN
      cd "$CHIPYARD_DIR/toolchains/$TOOLCHAIN"
      GITHASH="$(git rev-parse HEAD)"
      cd $RDIR
      echo "prebuilt hash: $PREBUILTHASH"
      echo "git      hash: $GITHASH"
      if [[ $PREBUILTHASH == $GITHASH && "$EC2FASTINSTALL" == "true" ]]; then
          FASTINSTALL=true
          echo "Using fast pre-compiled install for riscv-tools"
      else
          echo "Error: hash of precompiled toolchain doesn't match the riscv-tools submodule hash."
          exit
      fi
    else
          echo "Error: No precompiled toolchain for esp-tools or other non-native riscv-tools."
          exit 
    fi
fi

INSTALL_DIR="$TOOLCHAIN-install"
mkdir -p "$(pwd)/$INSTALL_DIR"

RISCV="$(pwd)/$INSTALL_DIR"

# install risc-v tools
export RISCV="$RISCV"

if [ "$FASTINSTALL" = true ]; then
    cd firesim-riscv-tools-prebuilt
    ./installrelease.sh
    mv distrib $RISCV
    # copy HASH in case user wants it later
    cp HASH $RISCV
    cd $RDIR
    rm -rf firesim-riscv-tools-prebuilt
else
    git -C $CHIPYARD_DIR submodule update --init --recursive toolchains/$TOOLCHAIN #--jobs 8
    cd "$CHIPYARD_DIR/toolchains/$TOOLCHAIN"
    export MAKEFLAGS="-j16"
    #build the actual toolchain
    #./build.sh
    source build.common
    echo "Starting RISC-V Toolchain build process"
    build_project riscv-fesvr --prefix=$RISCV
    build_project riscv-isa-sim --prefix=$RISCV --with-fesvr=$RISCV
    build_project riscv-gnu-toolchain --prefix=$RISCV
    CC= CXX= build_project riscv-pk --prefix=$RISCV --host=riscv64-unknown-elf
    build_project riscv-tests --prefix=$RISCV/riscv64-unknown-elf
    echo -e "\\nRISC-V Toolchain installation completed!"

    # build static libfesvr library for linking into firesim driver (or others)
    cd riscv-fesvr/build
    $CHIPYARD_DIR/scripts/build-static-libfesvr.sh
    cd $RDIR
    # build linux toolchain
    cd "$CHIPYARD_DIR/toolchains/$TOOLCHAIN/riscv-gnu-toolchain/build"
    make -j16 linux
    echo -e "\\nRISC-V Linux GNU Toolchain installation completed!"

fi

cd $RDIR

echo "export CHIPYARD_TOOLCHAIN_SOURCED=1" > env.sh
echo "export RISCV=$RISCV" >> env.sh
echo "export PATH=$RISCV/bin:$RDIR/$DTCversion:\$PATH" >> env.sh
echo "export LD_LIBRARY_PATH=$RISCV/lib" >> env.sh
echo "Toolchain Build Complete!"


if [ "$FASTINSTALL" = "false" ]; then
    # commands to run only on EC2
    # see if the instance info page exists. if not, we are not on ec2.
    # this is one of the few methods that works without sudo
    if wget -T 1 -t 3 -O /dev/null http://169.254.169.254/; then
        echo "Skipping RISC-V OpenOCD"
    else
        echo "Building RISC-V OpenOCD"
        check_version automake 1.14 "OpenOCD build"
        check_version autoconf 2.64 "OpenOCD build"
        build_project riscv-openocd --prefix=$RISCV --enable-remote-bitbang --enable-jtag_vpi --disable-werror
        echo -e "\\nRISC-V OpenOCD installation completed!"
    fi
fi
