package chipyard

import freechips.rocketchip.config.{Config}

// This file is designed to accompany a live tutorial, with slides.
// For each of 4 phases, participants will customize and build a
// small demonstration config.

// This file is designed to be used after running chipyard/scripts/tutorial-setup.sh,
// which removes the SHA3 accelerator RTL, and provides participants
// the experience of integrating external RTL.

// This file was originally developed for the cancelled ASPLOS-2020
// Chipyard tutorial. While the configs here work, the corresponding
// slideware has not yet been created

// NOTE: Configs should be read bottom-up, since they are applied bottom-up

// Tutorial Phase 1: Configure the cores, caches
class TutorialStarterConfig extends Config(
  // IOBinders specify how to connect to IOs in our TestHarness
  // These config fragments do not affect
  new chipyard.iobinders.WithUARTAdapter ++       // Connect a SimUART adapter to display UART on stdout
  new chipyard.iobinders.WithBlackBoxSimMem ++    // Connect simulated external memory
  new chipyard.iobinders.WithTieOffInterrupts ++  // Do not simulate external interrupts
  new chipyard.iobinders.WithTiedOffDebug ++      // Disconnect the debug module, since we use TSI for bring-up
  new chipyard.iobinders.WithSimSerial ++         // Connect external SimSerial widget to drive TSI

  // Config fragments below this line affect hardware generation
  // of the Top
  new testchipip.WithTSI ++                  // Add a TSI (Test Serial Interface)  widget to bring-up the core
  new chipyard.config.WithNoGPIO ++          // Disable GPIOs.
  new chipyard.config.WithBootROM ++         // Use the Chipyard BootROM
  new chipyard.config.WithRenumberHarts ++   // WithRenumberHarts fixes hartids heterogeneous designs, if design is not heterogeneous, this is a no-op
  new chipyard.config.WithUART ++            // Add a UART

  // CUSTOMIZE THE CORE
  // Uncomment out one (or multiple) of the lines below, and choose
  // how many cores you want.
  // new freechips.rocketchip.subsystem.WithNBigCores(1) ++ // Specify we want some number of Rocket cores
  // new boom.common.WithSmallBooms ++                      // Specify all BOOM cores should be Small-sized (NOTE: other options are Medium/Large/Mega)
  // new boom.common.WithNBoomCores(1) ++                   // Specify we want some number of BOOM cores

  // CUSTOMIZE the L2
  // Uncomment this line, and specify a size if you want to have a L2
  // new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=1, nWays=4, capacityKB=128) ++

  // For simpler designs, we want to minimize IOs on
  // our Top. These config fragments remove unnecessary
  // ports
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  // BaseConfig configures "bare" rocketchip system
  new freechips.rocketchip.system.BaseConfig
)


// Tutorial Phase 2: Integrate a TileLink or AXI4 MMIO device
class TutorialMMIOConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++

  new testchipip.WithTSI ++
  new chipyard.config.WithNoGPIO ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithRenumberHarts ++
  new chipyard.config.WithUART ++

  // Attach either a TileLink or AXI4 version of GCD
  // Uncomment one of the below lines
  // new chipyard.example.WithGCD(useAXI4=false) ++ // Use TileLink version
  // new chipyard.example.WithGCD(useAXI4=true) ++  // Use AXI4 version

  // For this demonstration we assume the base system is a single-core Rocket, for fast elaboration
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.system.BaseConfig
)

// Tutorial Phase 3: Integrate a SHA3 RoCC accelerator
class TutorialSha3Config extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++

  new testchipip.WithTSI ++
  new chipyard.config.WithNoGPIO ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithRenumberHarts ++
  new chipyard.config.WithUART ++

  // Uncomment this line once you added SHA3 to the build.sbt, and cloned the SHA3 repo
  // new sha3.WithSha3Accel ++

  // For this demonstration we assume the base system is a single-core Rocket, for fast elaboration
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.system.BaseConfig
)

// Tutorial Phase 4: Integrate a Black-box verilog version of the SHA3 RoCC accelerator
class TutorialSha3BlackBoxConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++

  new testchipip.WithTSI ++
  new chipyard.config.WithNoGPIO ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithRenumberHarts ++
  new chipyard.config.WithUART ++

  // Uncomment these lines once SHA3 is integrated
  // new sha3.WithSha3BlackBox ++ // Specify we want the Black-box verilog version of Sha3 Ctrl
  // new sha3.WithSha3Accel ++

  // For this demonstration we assume the base system is a single-core Rocket, for fast elaboration
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.system.BaseConfig
)
