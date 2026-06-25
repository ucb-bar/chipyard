#!/bin/bash

set -ex

# assuming you have env.sh sourced

CY_DIR=$(git rev-parse --show-toplevel)

# need nodisk version
marshal -v -d build $CY_DIR/software/firemarshal/example-workloads/linux-hello.yaml
FM_WORKLOAD_OUTPUT=$CY_DIR/software/firemarshal/images/firechip/linux-hello/linux-hello-bin-nodisk

## optional: verify it boots with spike (spike will use it's own uart for output)
#spike --pc=0x80000000 $FM_WORKLOAD_OUTPUT

# create dts to checkpoint with in spike
pushd sims/vcs
make CONFIG=dmiCheckpointingRocketConfig verilog
CHECKPOINT_DTS=$CY_DIR/sims/vcs/generated-src/chipyard.harness.TestHarness.dmiCheckpointingRocketConfig/chipyard.harness.TestHarness.dmiCheckpointingRocketConfig.dts
popd

# 0x8013 is the instruction "trigger" given in the workload
./scripts/generate-ckpt.sh -v -b $FM_WORKLOAD_OUTPUT -t 0x8013 -s $CHECKPOINT_DTS
LOADARCH_PATH=$CY_DIR/linux-hello-bin-nodisk.0x80000000.0x8013.0.customdts.loadarch

# make sure you can boot into it in target SW RTL simulation
pushd sims/vcs
make CONFIG=dmiCheckpointingRocketConfig run-binary LOADARCH=$LOADARCH_PATH
popd

echo "Successful checkpoint!"
