package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.rocket.{DCacheParams}

class TraceGenConfig extends Config(
  new chipyard.iobinders.WithSimAXIMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenTop ++
  new tracegen.WithTraceGen(List.fill(2) { DCacheParams(nMSHRs = 0, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.system.BaseConfig)

class NonBlockingTraceGenConfig extends Config(
  new chipyard.iobinders.WithSimAXIMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenTop ++
  new tracegen.WithTraceGen(List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.system.BaseConfig)

class BoomTraceGenConfig extends Config(
  new chipyard.iobinders.WithSimAXIMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenTop ++
  new tracegen.WithBoomTraceGen(List.fill(2) { DCacheParams(nMSHRs = 8, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.system.BaseConfig)

class NonBlockingTraceGenL2Config extends Config(
  new chipyard.iobinders.WithSimAXIMem ++
  new chipyard.iobinders.WithTraceGenSuccessBinder ++
  new chipyard.config.WithTracegenTop ++
  new tracegen.WithL2TraceGen(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.system.BaseConfig)
