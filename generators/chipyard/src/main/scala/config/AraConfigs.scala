package chipyard

import org.chipsalliance.cde.config.{Config}
import saturn.common.{VectorParams}

// Rocket-integrated configs
class Ara2LaneRocketConfig extends Config(
  new ara.WithAraRocketVectorUnit(2) ++
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)

class Ara4LaneRocketConfig extends Config(
  new ara.WithAraRocketVectorUnit(4) ++
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)


// Shuttle-integrated configs
class Ara2LaneShuttleConfig extends Config(
  new ara.WithAraShuttleVectorUnit(2) ++
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithNShuttleCores(1) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)
