package tracegen

import chisel3._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.coreplex.{WithMSICoherence, WithMICoherence}
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.groundtest.WithTraceGen
import freechips.rocketchip.rocket.DCacheParams

class TraceGenConfig extends Config(
  new WithTraceGen(List.fill(2){
    DCacheParams(nSets = 16, nWays = 1)
  }) ++ new DefaultConfig)

class MSITraceGenConfig extends Config(
  new WithMSICoherence ++ new TraceGenConfig)

class MITraceGenConfig extends Config(
  new WithMICoherence ++ new TraceGenConfig)
