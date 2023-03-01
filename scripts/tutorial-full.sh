#!/bin/bash

set -ex

CYDIR=$(git rev-parse --show-toplevel)

# 03 - building_custom_socs

cd $CYDIR/generators/chipyard/src/main/scala/config/
sed -i '120s/\/\/\( new sha3.*\)/\1/' TutorialConfigs.scala

cd $CYDIR/sims/verilator
make CONFIG=TutorialNoCConfig -j16
cd generated-src/chipyard*Tutorial*/
ls

cd $CYDIR/tests
make -j16

cd $CYDIR/sims/verilator
make CONFIG=TutorialNoCConfig run-binary-hex BINARY=../../tests/fft.riscv

cd $CYDIR/generators/sha3/software
./build.sh

cd $CYDIR/sims/verilator
make CONFIG=TutorialNoCConfig run-binary-hex BINARY=$SHA3SWDIR/sha3-rocc.riscv

## 06 - building_hw_firesim
#
#cd $FDIR
#ls
#
#cd $FDIR/sim
#make DESIGN=FireSim
#
#cd $FDIR/sim/generated-src/f1
#ls
#ls *
#
#cd $FDIR/deploy
## TODO: automate entering email
#firesim managerinit --platform f1
#ls
#
#cd $FDIR/deploy
#cat built-hwdb-entries/*
#
## TODO: remove this from slides
#cd ~/chipyard-afternoon/generators/sha3/software/
#marshal -d build marshal-configs/sha3-linux-test.yaml
#
## 07 - running_firesim_simulations
#
#eval "$(conda shell.bash hook)"
#cd ~/chipyard-afternoon
#source ./env.sh
#
#cd $FDIR
#source ./sourceme-f1-manager.sh
#
## TODO: Maybe off?
#sed -i 's/\(- f1.2xlarge: \).*/\1 1/' $FDIR/deploy/config_runtime.yaml
#sed -i 's/\(- f1.4xlarge: \).*/\1 0/' $FDIR/deploy/config_runtime.yaml
#sed -i 's/\(- f1.16xlarge: \).*/\1 0/' $FDIR/deploy/config_runtime.yaml
#sed -i 's/\(topology: \).*/\1 no_net_config/' $FDIR/deploy/config_runtime.yaml
#sed -i 's/\(no_net_num_nodes: \).*/\1 1/' $FDIR/deploy/config_runtime.yaml
#sed -i 's/\(default_hw_config: \).*/\1 firesim_rocket_singlecore_no_nic_l2_lbp/' $FDIR/deploy/config_runtime.yaml
#
#cd $FDIR/deploy
#cat built-hwdb-entries/firesim_rocket_singlecore_no_nic_l2_lbp >> config_hwdb.yaml
#
#firesim launchrunfarm
#firesim infrasetup
#firesim runworkload > /dev/null &
#
#sleep 1m # wait for simulation to start
#
## TODO: doesn't work since the uartlog is never copied back to the manager
#function waitForBoot {
#    cd $FDIR/deploy/results-workload/
#    LAST_DIR=$(ls | tail -n1)
#    if [ -d "$LAST_DIR" ]; then
#        while ! grep -i "Welcome to Buildroot" $LAST_DIR/*/uartlog;
#        do
#            echo "Waiting on boot";
#            sleep 2;
#        done
#    else
#        echo "Unable to find output directory"
#        exit 111
#    fi
#}
#export -f waitForBoot
#timeout 10m bash -c waitForBoot
#
#kill $(jobs -p) # should be noop
#firesim terminaterunfarm -q
