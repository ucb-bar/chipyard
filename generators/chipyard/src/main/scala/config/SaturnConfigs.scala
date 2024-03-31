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
  new chipyard.config.WithSystemBusWidth(128) ++
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

class REFV256D128C1RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 128, VectorParams.refParams.copy(hazardingMultiplier=1)) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV256D128C2RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 128, VectorParams.refParams.copy(hazardingMultiplier=2)) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV256D128C3RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 128, VectorParams.refParams.copy(hazardingMultiplier=3)) ++
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

class DMAV256D256RocketConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new saturn.rocket.WithRocketVectorUnit(256, 256, VectorParams.dmaParams) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new freechips.rocketchip.subsystem.WithRocketCease(false) ++
  new freechips.rocketchip.subsystem.WithRocketDebugROB ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// Shuttle-integrated configs
class REFV256D64ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(64) ++
  new saturn.shuttle.WithShuttleVectorUnit(256, 64, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV256D128ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new saturn.shuttle.WithShuttleVectorUnit(256, 128, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class DSPV256D128ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new saturn.shuttle.WithShuttleVectorUnit(256, 128, VectorParams.dspParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class GENV256D128ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new saturn.shuttle.WithShuttleVectorUnit(256, 128, VectorParams.genParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV512D128ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new saturn.shuttle.WithShuttleVectorUnit(512, 128, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class DSPV512D128ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new saturn.shuttle.WithShuttleVectorUnit(512, 128, VectorParams.dspParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class GENV512D128ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new saturn.shuttle.WithShuttleVectorUnit(512, 128, VectorParams.genParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV256D256ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new saturn.shuttle.WithShuttleVectorUnit(256, 256, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(32) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV512D256ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new saturn.shuttle.WithShuttleVectorUnit(512, 256, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(32) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class REFV512D512ShuttleConfig extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new saturn.shuttle.WithShuttleVectorUnit(512, 512, VectorParams.refParams) ++
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithShuttleTileBeatBytes(64) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)

class QuadREFV512D256ShuttleConfig extends Config(
  new chipyard.config.WithSystemBusWidth(256) ++
  new saturn.shuttle.WithShuttleVectorUnit(512, 256, VectorParams.refParams) ++
  new shuttle.common.WithShuttleTileBeatBytes(32) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithNShuttleCores(4) ++
  new chipyard.config.AbstractConfig)

class DSPMultiSaturnConfig extends Config(
  new chipyard.config.WithSystemBusWidth(256) ++

  new saturn.shuttle.WithShuttleVectorUnit(512, 256, VectorParams.refParams) ++
  new shuttle.common.WithL1DCacheIOMSHRs(8) ++
  new shuttle.common.WithTCM ++
  new shuttle.common.WithShuttleTileBeatBytes(32) ++
  new shuttle.common.WithNShuttleCores(2) ++             // dsp-Saturn

  new saturn.rocket.WithRocketVectorUnit(256, 256, VectorParams.dmaParams, cores = Some(Seq(1)), useL1DCache = false) ++
  new chipyard.config.WithRocketMSHRs(1, 6) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++ // dma-Saturn

  new freechips.rocketchip.subsystem.WithNBigCores(1) ++ // ctrl core
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)
