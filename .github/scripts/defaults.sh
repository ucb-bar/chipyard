#!/bin/bash

# make parallelism
CI_MAKE_NPROC=8
# chosen based on a 24c system shared with 1 other project
REMOTE_MAKE_NPROC=4

# remote variables
# CI_DIR is defined externally based on the GH repository secret BUILDDIR

REMOTE_PREFIX=$CI_DIR/${GITHUB_REPOSITORY#*/}-${GITHUB_REF_NAME//\//-}
REMOTE_WORK_DIR=$GITHUB_WORKSPACE
REMOTE_CHIPYARD_DIR=$GITHUB_WORKSPACE
REMOTE_SIM_DIR=$REMOTE_CHIPYARD_DIR/sims/verilator
REMOTE_FIRESIM_DIR=$REMOTE_CHIPYARD_DIR/sims/firesim/sim
REMOTE_FPGA_DIR=$REMOTE_CHIPYARD_DIR/fpga
REMOTE_JAVA_OPTS="-Xmx10G -Xss8M"
# Disable the supershell to greatly improve the readability of SBT output when captured by Circle CI
REMOTE_SBT_OPTS="-Dsbt.ivy.home=$REMOTE_WORK_DIR/.ivy2 -Dsbt.supershell=false -Dsbt.global.base=$REMOTE_WORK_DIR/.sbt -Dsbt.boot.directory=$REMOTE_WORK_DIR/.sbt/boot"

# local variables (aka within the docker container)
LOCAL_CHIPYARD_DIR=$GITHUB_WORKSPACE
LOCAL_SIM_DIR=$LOCAL_CHIPYARD_DIR/sims/verilator
LOCAL_FIRESIM_DIR=$LOCAL_CHIPYARD_DIR/sims/firesim/sim

# key value store to get the build groups
declare -A grouping
grouping["group-cores"]="chipyard-cva6 chipyard-ibex chipyard-rocket chipyard-hetero chipyard-boom chipyard-sodor chipyard-digitaltop chipyard-multiclock-rocket chipyard-nomem-scratchpad chipyard-spike chipyard-clone"
grouping["group-peripherals"]="chipyard-dmirocket chipyard-blkdev chipyard-spiflashread chipyard-spiflashwrite chipyard-mmios chipyard-lbwif"
grouping["group-accels"]="chipyard-fftgenerator chipyard-nvdla chipyard-mempress chipyard-sha3 chipyard-hwacha chipyard-gemmini chipyard-streaming-fir chipyard-streaming-passthrough"
grouping["group-constellation"]="chipyard-constellation"
grouping["group-tracegen"]="tracegen tracegen-boom"
grouping["group-other"]="icenet testchipip constellation"
grouping["group-fpga"]="arty vcu118 vc707"

# key value store to get the build strings
declare -A mapping
mapping["chipyard-rocket"]=""
mapping["chipyard-dmirocket"]=" CONFIG=dmiRocketConfig"
mapping["chipyard-lbwif"]=" CONFIG=LBWIFRocketConfig"
mapping["chipyard-sha3"]=" CONFIG=Sha3RocketConfig"
mapping["chipyard-mempress"]=" CONFIG=MempressRocketConfig"
mapping["chipyard-digitaltop"]=" TOP=DigitalTop"
mapping["chipyard-streaming-fir"]=" CONFIG=StreamingFIRRocketConfig"
mapping["chipyard-streaming-passthrough"]=" CONFIG=StreamingPassthroughRocketConfig"
mapping["chipyard-hetero"]=" CONFIG=LargeBoomAndRocketConfig"
mapping["chipyard-boom"]=" CONFIG=SmallBoomConfig"
mapping["chipyard-spike"]=" CONFIG=SpikeConfig"
mapping["chipyard-blkdev"]=" CONFIG=SimBlockDeviceRocketConfig"
mapping["chipyard-hwacha"]=" CONFIG=HwachaRocketConfig"
mapping["chipyard-gemmini"]=" CONFIG=GemminiRocketConfig"
mapping["chipyard-cva6"]=" CONFIG=CVA6Config"
mapping["chipyard-ibex"]=" CONFIG=IbexConfig"
mapping["chipyard-spiflashread"]=" CONFIG=LargeSPIFlashROMRocketConfig"
mapping["chipyard-spiflashwrite"]=" CONFIG=SmallSPIFlashRocketConfig"
mapping["chipyard-mmios"]=" CONFIG=MMIORocketConfig verilog"
mapping["chipyard-cloneboom"]=" CONFIG=Cloned64MegaBoomConfig verilog"
mapping["tracegen"]=" CONFIG=NonBlockingTraceGenL2Config"
mapping["tracegen-boom"]=" CONFIG=BoomTraceGenConfig"
mapping["chipyard-nvdla"]=" CONFIG=SmallNVDLARocketConfig"
mapping["chipyard-sodor"]=" CONFIG=Sodor5StageConfig"
mapping["chipyard-multiclock-rocket"]=" CONFIG=MulticlockRocketConfig"
mapping["chipyard-nomem-scratchpad"]=" CONFIG=MMIOScratchpadOnlyRocketConfig"
mapping["chipyard-fftgenerator"]=" CONFIG=FFTRocketConfig"
mapping["chipyard-constellation"]=" CONFIG=SharedNoCConfig"

mapping["constellation"]=" SUB_PROJECT=constellation"
mapping["firesim"]="SCALA_TEST=firesim.firesim.RocketNICF1Tests"
mapping["fireboom"]="SCALA_TEST=firesim.firesim.BoomF1Tests"
mapping["icenet"]="SUB_PROJECT=icenet"
mapping["testchipip"]="SUB_PROJECT=testchipip"

mapping["arty"]="SUB_PROJECT=arty verilog"
mapping["vcu118"]="SUB_PROJECT=vcu118 verilog"
mapping["vc707"]="SUB_PROJECT=vc707 verilog"
