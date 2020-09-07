package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.rocket.{DCacheParams}

class TraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new tracegen.WithTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 0, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)

class NonBlockingTraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new tracegen.WithTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)

class BoomTraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new tracegen.WithBoomTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 8, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)

class NonBlockingTraceGenL2Config extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new tracegen.WithL2TraceGen()(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)

class NonBlockingTraceGenL2RingConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new tracegen.WithL2TraceGen()(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new testchipip.WithRingSystemBus ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)
