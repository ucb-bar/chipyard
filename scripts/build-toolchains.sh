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
    echo "usage: ./scripts/build-toolchains.sh [riscv] [hwacha] [ firesim | --firesim] [--submodules-only]"
    echo "   riscv: if set, builds the riscv toolchain (this is also the default)"
    echo "   hwacha: if set, builds esp-tools toolchain"
    echo "   firesim: if set, pulls in a pre-compiled RISC-V toolchain for an EC2 manager instance"
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
FIRESIMINSTALL="false"
while test $# -gt 0
do
   case "$1" in
        riscv)
            TOOLCHAIN="riscv-tools"
            ;;
        hwacha)
            TOOLCHAIN="esp-tools"
            ;;
        firesim | --firesim) # I don't want to break this api
            FIRESIMINSTALL=true
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

if [ "TOOLCHAINS" = "riscv-tools" ]; then
    if [ "$FIRESIMINSTALL" = "true" ]; then
      cd sims/firesim/
      git clone https://github.com/firesim/firesim-riscv-tools-prebuilt.git
      cd firesim-riscv-tools-prebuilt
      git checkout 5fee18421a32058ab339572128201f4904354aaa
      PREBUILTHASH="$(cat HASH)"
      cd $RDIR/toolchain/riscv-tools/
      GITHASH="git rev-parse HEAD"
      cd $RDIR
      if [[ "$PREBUILTHASH" == "$GITHASH" && "$FIRESIMINSTALL" == "true" ]]; then
          FASTINSTALL=true
          #just call a fireism build-toolchain script?
      fi
    fi
fi

INSTALL_DIR="$TOOLCHAIN-install"
mkdir -p "$(pwd)/$INSTALL_DIR"

RISCV="$(pwd)/$INSTALL_DIR"

# install risc-v tools
export RISCV="$RISCV"

if [ "$FASTINSTALL" = true ]; then
    cd sims/firesim/firesim-riscv-tools-prebuilt
    ./installrelease.sh
    mv distrib $(pwd)/$INSTALL_DIR
    # copy HASH in case user wants it later
    cp HASH $(pwd)/$INSTALL_DIR
    cd $RDIR
    rm -rf sims/firesim/firesim-riscv-tools-prebuilt
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
    $CHIPYARD_DIR/sims/firesim/scripts/build-static-libfesvr.sh
    cd $RDIR
    # build linux toolchain
    cd "$CHIPYARD_DIR/toolchains/$TOOLCHAIN/riscv-gnu-toolchain/build"
    make -j16 linux
    echo -e "\\nRISC-V Linux GNU Toolchain installation completed!"

    if [ "$FIRESIMINSTALL" = "false" ]; then
        check_version automake 1.14 "OpenOCD build"
        check_version autoconf 2.64 "OpenOCD build"
        build_project riscv-openocd --prefix=$RISCV --enable-remote-bitbang --enable-jtag_vpi --disable-werror      
        echo -e "\\nRISC-V OpenOCD installation completed!"
    fi

fi

cd $RDIR

echo "export CHIPYARD_TOOLCHAIN_SOURCED=1" > env.sh
echo "export RISCV=$RISCV" > env.sh
echo "export PATH=$RISCV/bin:$RDIR/$DTCversion:\$PATH" >> env.sh
echo "export LD_LIBRARY_PATH=$RISCV/lib" >> env.sh
echo "Toolchain Build Complete!"
