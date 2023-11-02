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

# local variables (aka within the docker container)
LOCAL_CHIPYARD_DIR=$GITHUB_WORKSPACE
LOCAL_SIM_DIR=$LOCAL_CHIPYARD_DIR/sims/verilator
LOCAL_FIRESIM_DIR=$LOCAL_CHIPYARD_DIR/sims/firesim/sim

# CI uses temp directories with very long names
# explicitly force socket creation to use /tmp to avoid name length errors
# https://github.com/sbt/sbt/pull/6887
REMOTE_JAVA_TMP_DIR=$(mktemp -d -t ci-cy-XXXXXXXX)
REMOTE_COURSIER_CACHE=$REMOTE_WORK_DIR/.coursier-cache

# key value store to get the build groups
declare -A grouping
grouping["group-cores"]="chipyard-cva6 chipyard-ibex chipyard-rocket chipyard-hetero chipyard-boom chipyard-sodor chipyard-digitaltop chipyard-multiclock-rocket chipyard-nomem-scratchpad chipyard-spike chipyard-clone chipyard-prefetchers chipyard-shuttle"
grouping["group-peripherals"]="chipyard-dmirocket chipyard-dmiboom chipyard-spiflashwrite chipyard-mmios chipyard-nocores chipyard-manyperipherals chipyard-chiplike chipyard-tethered"
grouping["group-accels"]="chipyard-mempress chipyard-sha3 chipyard-hwacha chipyard-gemmini chipyard-manymmioaccels chipyard-nvdla"
grouping["group-constellation"]="chipyard-constellation"
grouping["group-tracegen"]="tracegen tracegen-boom"
grouping["group-other"]="icenet testchipip constellation rocketchip-amba rocketchip-tlsimple rocketchip-tlwidth rocketchip-tlxbar"
grouping["group-fpga"]="arty arty100t nexysvideo vc707 vcu118"

# key value store to get the build strings
declare -A mapping
mapping["chipyard-rocket"]=" CONFIG=QuadChannelRocketConfig"
mapping["chipyard-dmirocket"]=" CONFIG=dmiRocketConfig"
mapping["chipyard-sha3"]=" CONFIG=Sha3RocketConfig"
mapping["chipyard-mempress"]=" CONFIG=MempressRocketConfig"
mapping["chipyard-prefetchers"]=" CONFIG=PrefetchingRocketConfig"
mapping["chipyard-digitaltop"]=" TOP=DigitalTop"
mapping["chipyard-manymmioaccels"]=" CONFIG=ManyMMIOAcceleratorRocketConfig"
mapping["chipyard-nvdla"]=" CONFIG=SmallNVDLARocketConfig verilog"
mapping["chipyard-hetero"]=" CONFIG=LargeBoomAndRocketConfig"
mapping["chipyard-boom"]=" CONFIG=MediumBoomCosimConfig"
mapping["chipyard-dmiboom"]=" CONFIG=dmiMediumBoomCosimConfig"
mapping["chipyard-spike"]=" CONFIG=SpikeConfig EXTRA_SIM_FLAGS='+spike-ipc=10'"
mapping["chipyard-hwacha"]=" CONFIG=HwachaRocketConfig"
mapping["chipyard-gemmini"]=" CONFIG=GemminiRocketConfig"
mapping["chipyard-cva6"]=" CONFIG=CVA6Config"
mapping["chipyard-ibex"]=" CONFIG=IbexConfig"
mapping["chipyard-spiflashwrite"]=" CONFIG=SmallSPIFlashRocketConfig EXTRA_SIM_FLAGS='+spiflash0=${LOCAL_CHIPYARD_DIR}/tests/spiflash.img'"
mapping["chipyard-manyperipherals"]=" CONFIG=ManyPeripheralsRocketConfig EXTRA_SIM_FLAGS='+spiflash0=${LOCAL_CHIPYARD_DIR}/tests/spiflash.img'"
mapping["chipyard-chiplike"]=" CONFIG=ChipLikeRocketConfig MODEL=FlatTestHarness MODEL_PACKAGE=chipyard.example verilog"
mapping["chipyard-tethered"]=" CONFIG=VerilatorCITetheredChipLikeRocketConfig"
mapping["chipyard-cloneboom"]=" CONFIG=Cloned64MegaBoomConfig verilog"
mapping["chipyard-nocores"]=" CONFIG=NoCoresConfig verilog"
mapping["tracegen"]=" CONFIG=NonBlockingTraceGenL2Config"
mapping["tracegen-boom"]=" CONFIG=BoomTraceGenConfig"
mapping["chipyard-sodor"]=" CONFIG=Sodor5StageConfig"
mapping["chipyard-shuttle"]=" CONFIG=ShuttleConfig"
mapping["chipyard-multiclock-rocket"]=" CONFIG=MulticlockRocketConfig"
mapping["chipyard-nomem-scratchpad"]=" CONFIG=MMIOScratchpadOnlyRocketConfig"
mapping["chipyard-constellation"]=" CONFIG=SharedNoCConfig"

mapping["constellation"]=" SUB_PROJECT=constellation"
mapping["firesim"]="SCALA_TEST=firesim.firesim.RocketNICF1Tests"
mapping["fireboom"]="SCALA_TEST=firesim.firesim.BoomF1Tests"
mapping["icenet"]="SUB_PROJECT=icenet"
mapping["testchipip"]="SUB_PROJECT=testchipip"
mapping["rocketchip-amba"]="SUB_PROJECT=rocketchip CONFIG=AMBAUnitTestConfig"
mapping["rocketchip-tlsimple"]="SUB_PROJECT=rocketchip CONFIG=TLSimpleUnitTestConfig"
mapping["rocketchip-tlwidth"]="SUB_PROJECT=rocketchip CONFIG=TLWidthUnitTestConfig"
mapping["rocketchip-tlxbar"]="SUB_PROJECT=rocketchip CONFIG=TLXbarUnitTestConfig"

mapping["arty"]="SUB_PROJECT=arty verilog"
mapping["arty100t"]="SUB_PROJECT=arty100t verilog"
mapping["nexysvideo"]="SUB_PROJECT=nexysvideo verilog"
mapping["vc707"]="SUB_PROJECT=vc707 verilog"
mapping["vcu118"]="SUB_PROJECT=vcu118 verilog"
