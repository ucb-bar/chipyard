package chipyard

import org.chipsalliance.cde.config.{Config}
import saturn.common.{VectorParams}

// Rocket-integrated configs
class MINV64D64RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(64, 64, VectorParams.minParams) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MINV128D64RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(128, 64, VectorParams.minParams) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MINV256D64RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 64, VectorParams.minParams) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV128D128RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(128, 128, VectorParams.refParams) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV256D64RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 64, VectorParams.refParams) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV256D128RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 128, VectorParams.refParams) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV512D128RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(512, 128, VectorParams.refParams) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV512D256RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(512, 256, VectorParams.refParams) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// Shuttle-integrated configs
class REFV256D64ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.shuttle.WithShuttleVectorUnit(256, 64, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)
