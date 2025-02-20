#!/bin/bash

# make parallelism
CI_MAKE_NPROC=8
# chosen based on a 24c system shared with 1 other project
REMOTE_MAKE_NPROC=4

# remote variables
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
grouping["group-cores"]="chipyard-cva6 chipyard-ibex chipyard-rocket chipyard-hetero chipyard-boomv3 chipyard-boomv4 chipyard-sodor chipyard-digitaltop chipyard-multiclock-rocket chipyard-nomem-scratchpad chipyard-spike chipyard-clone chipyard-prefetchers chipyard-shuttle chipyard-shuttle3 chipyard-vexiiriscv chipyard-tacit-rocket chipyard-radiance"
grouping["group-peripherals"]="chipyard-dmirocket chipyard-dmiboomv3 chipyard-dmiboomv4 chipyard-spiflashwrite chipyard-mmios chipyard-nocores chipyard-manyperipherals chipyard-chiplike chipyard-tethered chipyard-symmetric chipyard-llcchiplet"
grouping["group-accels"]="chipyard-compressacc chipyard-mempress chipyard-gemmini chipyard-manymmioaccels chipyard-nvdla chipyard-aes256ecb chipyard-rerocc chipyard-rocketvector chipyard-shuttlevector chipyard-hlsacc" # chipyard-shuttleara - Add when Ara works again
grouping["group-constellation"]="chipyard-constellation"
grouping["group-tracegen"]="tracegen tracegen-boomv3 tracegen-boomv4"
grouping["group-other"]="icenet testchipip constellation rocketchip-amba rocketchip-tlsimple rocketchip-tlwidth rocketchip-tlxbar chipyard-clusters"
grouping["group-fpga"]="arty35t arty100t nexysvideo vc707 vcu118"

# key value store to get the build strings
declare -A mapping
mapping["chipyard-rocket"]=" CONFIG=QuadChannelRocketConfig"
# TODO: Verilator chokes on cospikeCheckpointingRocketConfig
# mapping["chipyard-dmirocket"]=" CONFIG=dmiCospikeCheckpointingRocketConfig"
mapping["chipyard-dmirocket"]=" CONFIG=dmiRocketConfig"
mapping["chipyard-mempress"]=" CONFIG=MempressRocketConfig"
mapping["chipyard-compressacc"]=" CONFIG=ZstdCompressorRocketConfig"
mapping["chipyard-hlsacc"]=" CONFIG=GCDHLSRocketConfig"
mapping["chipyard-prefetchers"]=" CONFIG=PrefetchingRocketConfig"
mapping["chipyard-digitaltop"]=" TOP=DigitalTop"
mapping["chipyard-manymmioaccels"]=" CONFIG=ManyMMIOAcceleratorRocketConfig"
mapping["chipyard-nvdla"]=" CONFIG=SmallNVDLARocketConfig verilog"
mapping["chipyard-hetero"]=" CONFIG=LargeBoomAndRocketConfig"
mapping["chipyard-boomv3"]=" CONFIG=MediumBoomV3CosimConfig"
mapping["chipyard-dmiboomv3"]=" CONFIG=dmiCheckpointingMediumBoomV3Config"
mapping["chipyard-boomv4"]=" CONFIG=MediumBoomV4CosimConfig"
mapping["chipyard-dmiboomv4"]=" CONFIG=dmiCheckpointingMediumBoomV4Config"
mapping["chipyard-spike"]=" CONFIG=SpikeZicntrConfig EXTRA_SIM_FLAGS='+spike-ipc=10'"
mapping["chipyard-gemmini"]=" CONFIG=GemminiRocketConfig"
mapping["chipyard-cva6"]=" CONFIG=CVA6Config"
mapping["chipyard-ibex"]=" CONFIG=IbexConfig"
mapping["chipyard-vexiiriscv"]=" CONFIG=VexiiRiscvConfig"
mapping["chipyard-spiflashwrite"]=" CONFIG=SmallSPIFlashRocketConfig EXTRA_SIM_FLAGS='+spiflash0=${LOCAL_CHIPYARD_DIR}/tests/spiflash.img'"
mapping["chipyard-manyperipherals"]=" CONFIG=ManyPeripheralsRocketConfig EXTRA_SIM_FLAGS='+spiflash0=${LOCAL_CHIPYARD_DIR}/tests/spiflash.img'"
mapping["chipyard-chiplike"]=" CONFIG=ChipLikeRocketConfig MODEL=FlatTestHarness MODEL_PACKAGE=chipyard.example verilog"
mapping["chipyard-tethered"]=" CONFIG=VerilatorCITetheredChipLikeRocketConfig"
mapping["chipyard-symmetric"]=" CONFIG=MultiSimMultiLinkSymmetricChipletRocketConfig"
mapping["chipyard-llcchiplet"]=" CONFIG=MultiSimLLCChipletRocketConfig"
mapping["chipyard-cloneboom"]=" CONFIG=Cloned64MegaBoomV3Config verilog"
mapping["chipyard-nocores"]=" CONFIG=NoCoresConfig verilog"
mapping["tracegen"]=" CONFIG=NonBlockingTraceGenL2Config"
mapping["tracegen-boomv3"]=" CONFIG=BoomV3TraceGenConfig"
mapping["tracegen-boomv4"]=" CONFIG=BoomV4TraceGenConfig"
mapping["chipyard-sodor"]=" CONFIG=Sodor5StageConfig"
mapping["chipyard-shuttle"]=" CONFIG=ShuttleConfig"
mapping["chipyard-shuttle3"]=" CONFIG=Shuttle3WideConfig"
mapping["chipyard-tacit-rocket"]=" CONFIG=TacitRocketConfig"
mapping["chipyard-multiclock-rocket"]=" CONFIG=MulticlockRocketConfig"
mapping["chipyard-nomem-scratchpad"]=" CONFIG=MMIOScratchpadOnlyRocketConfig"
mapping["chipyard-constellation"]=" CONFIG=SharedNoCConfig"
mapping["chipyard-clusters"]=" CONFIG=ClusteredRocketConfig verilog"
mapping["chipyard-aes256ecb"]=" CONFIG=AES256ECBRocketConfig"
mapping["chipyard-rerocc"]=" CONFIG=ReRoCCTestConfig"
mapping["chipyard-rocketvector"]=" CONFIG=MINV128D64RocketConfig"
mapping["chipyard-shuttlevector"]=" CONFIG=GENV256D128ShuttleConfig"
mapping["chipyard-shuttleara"]=" CONFIG=V4096Ara2LaneShuttleConfig USE_ARA=1 verilog"
mapping["chipyard-radiance"]=" CONFIG=RadianceFP16ClusterConfig verilog"

mapping["constellation"]=" SUB_PROJECT=constellation"
mapping["icenet"]="SUB_PROJECT=icenet"
mapping["testchipip"]="SUB_PROJECT=testchipip"
mapping["rocketchip-amba"]="SUB_PROJECT=rocketchip CONFIG=AMBAUnitTestConfig"
mapping["rocketchip-tlsimple"]="SUB_PROJECT=rocketchip CONFIG=TLSimpleUnitTestConfig"
mapping["rocketchip-tlwidth"]="SUB_PROJECT=rocketchip CONFIG=TLWidthUnitTestConfig"
mapping["rocketchip-tlxbar"]="SUB_PROJECT=rocketchip CONFIG=TLXbarUnitTestConfig"

mapping["arty35t"]="SUB_PROJECT=arty35t verilog"
mapping["arty100t"]="SUB_PROJECT=arty100t verilog"
mapping["nexysvideo"]="SUB_PROJECT=nexysvideo verilog"
mapping["vc707"]="SUB_PROJECT=vc707 verilog"
mapping["vcu118"]="SUB_PROJECT=vcu118 verilog"
