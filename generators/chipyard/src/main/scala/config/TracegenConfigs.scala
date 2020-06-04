package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.rocket.{DCacheParams}

class TraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new tracegen.WithTraceGen(List.fill(2) { DCacheParams(nMSHRs = 0, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class NonBlockingTraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new tracegen.WithTraceGen(List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class BoomTraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new tracegen.WithBoomTraceGen(List.fill(2) { DCacheParams(nMSHRs = 8, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class NonBlockingTraceGenL2Config extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new tracegen.WithL2TraceGen(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class NonBlockingTraceGenL2RingConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenSystem ++
  new tracegen.WithL2TraceGen(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new testchipip.WithRingSystemBus ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class DRAMCacheTraceGenConfig extends Config(
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.iobinders.WithDRAMCacheBinder ++
  new chipyard.config.WithDRAMCacheTracegenSystem ++
  new tracegen.WithDRAMCacheTraceGen(
    List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new chipyard.config.WithDRAMCacheExtentTableInit ++
  new chipyard.config.WithDRAMCacheKey(4, 4) ++
  new chipyard.config.WithMemBladeKey ++
  new icenet.WithIceNIC ++
  new freechips.rocketchip.subsystem.WithExtMemSize(2L << 30) ++
  new freechips.rocketchip.system.BaseConfig)
