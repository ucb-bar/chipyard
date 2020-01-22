#!/bin/bash

sudo apt-get install -y libgmp-dev libmpfr-dev libmpc-dev zlib1g-dev vim git default-jdk default-jre
# install sbt: https://www.scala-sbt.org/release/docs/Installing-sbt-on-Linux.html
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install -y sbt
sudo apt-get install -y texinfo gengetopt
sudo apt-get install -y libxpat1-dev libusb-dev libncurses5-dev cmake
# deps for poky
sudo apt-get install -y python3.6 patch diffstat texi2html texinfo subversion chrpath git wget
# deps for qemu
sudo apt-get install -y libgtk-3-dev
# deps for firemarshal
sudo apt-get install -y python3-pip python3.6-dev rsync
# Install GNU make 4.x (needed to cross-compile glibc 2.28+)
sudo apt-get install -y build-essential
# install DTC
sudo apt-get install -y device-tree-compiler
