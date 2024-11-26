package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.rocket.{DCacheParams}

class AbstractTraceGenConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithBlackBoxSimMem ++
  new chipyard.harness.WithTraceGenSuccess ++
  new chipyard.harness.WithClockFromHarness ++
  new chipyard.harness.WithResetFromHarness ++
  new chipyard.iobinders.WithAXI4MemPunchthrough ++
  new chipyard.iobinders.WithTraceGenSuccessPunchthrough ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("sbus"), Nil)) ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemClockIO ++
  new chipyard.config.WithUniformBusFrequencies(1000.0) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)



class TraceGenConfig extends Config(
  new tracegen.WithTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 0, nSets = 16, nWays = 2) }) ++
  new AbstractTraceGenConfig)

class NonBlockingTraceGenConfig extends Config(
  new tracegen.WithTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new AbstractTraceGenConfig)

class BoomV3TraceGenConfig extends Config(
  new tracegen.WithBoomV3TraceGen()(List.fill(2) { DCacheParams(nMSHRs = 8, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)

class BoomV4TraceGenConfig extends Config(
  new tracegen.WithBoomV4TraceGen()(List.fill(2) { DCacheParams(nMSHRs = 8, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)

class NonBlockingTraceGenL2Config extends Config(
  new tracegen.WithL2TraceGen()(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)

class NonBlockingTraceGenL2RingConfig extends Config(
  new tracegen.WithL2TraceGen()(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new testchipip.soc.WithRingSystemBus ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)
