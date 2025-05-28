package chipyard

import chisel3._

import org.chipsalliance.cde.config.{Config}

class BaseRVLIWConfig extends Config(
  new testchipip.soc.WithNoScratchpads ++                      // No scratchpads
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++

  new chipyard.harness.WithHarnessBinderClockFreqMHz(500.0) ++ // sppeds up binary loading
  new chipyard.config.AbstractConfig
)

class RVLIWFixed4Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.Fixed4Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWFixedRVC4Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.FixedRVC4Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWSwizzling4Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.Swizzling4Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWSwizzlingRVC4Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.SwizzlingRVC4Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWHybrid4Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.Hybrid4Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWRISC4Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.RISC4Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWBase1Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.Base1Params(debugROB=true)) ++
  new BaseRVLIWConfig)

class RVLIWBaseRVC1Config extends Config(
  new chipyard.harness.WithCospike ++
  new chipyard.config.WithTraceIO ++

  new rvliw.common.WithRVLIWCore(rvliw.common.BaseRVC1Params(debugROB=true)) ++
  new BaseRVLIWConfig)
