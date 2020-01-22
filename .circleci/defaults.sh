#!/bin/bash

copy () {
    rsync -avzp -e 'ssh' $1 $2
}

run () {
    ssh -o "StrictHostKeyChecking no" -t $SERVER $@
}

run_script () {
    ssh -o "StrictHostKeyChecking no" -t $SERVER 'bash -s' < $1 "$2"
}

clean () {
    # remove remote work dir
    run "rm -rf $REMOTE_WORK_DIR"
}

# make parallelism
NPROC=8

# remote variables
REMOTE_WORK_DIR=$CI_DIR/$CIRCLE_PROJECT_REPONAME-$CIRCLE_BRANCH-$CIRCLE_SHA1-$CIRCLE_JOB
REMOTE_RISCV_DIR=$REMOTE_WORK_DIR/riscv-tools-install
REMOTE_ESP_DIR=$REMOTE_WORK_DIR/esp-tools-install
REMOTE_CHIPYARD_DIR=$REMOTE_WORK_DIR/chipyard
REMOTE_VERILATOR_DIR=$REMOTE_WORK_DIR/verilator
REMOTE_SIM_DIR=$REMOTE_CHIPYARD_DIR/sims/verilator
REMOTE_FIRESIM_DIR=$REMOTE_CHIPYARD_DIR/sims/firesim/sim
REMOTE_JAVA_ARGS="-Xmx8G -Xss8M -Dsbt.ivy.home=$REMOTE_WORK_DIR/.ivy2 -Dsbt.global.base=$REMOTE_WORK_DIR/.sbt -Dsbt.boot.directory=$REMOTE_WORK_DIR/.sbt/boot"

# local variables (aka within the docker container)
LOCAL_CHECKOUT_DIR=$HOME/project
LOCAL_RISCV_DIR=$HOME/riscv-tools-install
LOCAL_ESP_DIR=$HOME/esp-tools-install
LOCAL_CHIPYARD_DIR=$LOCAL_CHECKOUT_DIR
LOCAL_VERILATOR_DIR=$HOME/verilator
LOCAL_SIM_DIR=$LOCAL_CHIPYARD_DIR/sims/verilator
LOCAL_FIRESIM_DIR=$LOCAL_CHIPYARD_DIR/sims/firesim/sim

# key value store to get the build strings
declare -A mapping
mapping["chipyard-rocket"]="SUB_PROJECT=chipyard"
mapping["chipyard-hetero"]="SUB_PROJECT=chipyard CONFIG=LargeBoomAndRocketConfig"
mapping["chipyard-boom"]="SUB_PROJECT=chipyard CONFIG=SmallBoomConfig"
mapping["rocketchip"]="SUB_PROJECT=rocketchip"
mapping["chipyard-blkdev"]="SUB_PROJECT=chipyard CONFIG=SimBlockDeviceRocketConfig"
mapping["chipyard-hwacha"]="SUB_PROJECT=chipyard CONFIG=HwachaRocketConfig"
mapping["chipyard-gemmini"]="SUB_PROJECT=chipyard CONFIG=GemminiRocketConfig"
mapping["tracegen"]="SUB_PROJECT=tracegen CONFIG=NonBlockingTraceGenL2Config"
mapping["tracegen-boom"]="SUB_PROJECT=tracegen CONFIG=BoomTraceGenConfig"
mapping["firesim"]="DESIGN=FireSim TARGET_CONFIG=DDR3FRFCFSLLC4MB_FireSimRocketChipConfig PLATFORM_CONFIG=BaseF1Config"
mapping["fireboom"]="DESIGN=FireSim TARGET_CONFIG=DDR3FRFCFSLLC4MB_FireSimBoomConfig PLATFORM_CONFIG=BaseF1Config"
