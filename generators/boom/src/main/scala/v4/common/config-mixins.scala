//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package boom.v4.common

import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.{BootROMParams}
import freechips.rocketchip.prci.{SynchronousCrossing, AsynchronousCrossing, RationalCrossing}
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import boom.v4.ifu._
import boom.v4.exu._
import boom.v4.lsu._

// ---------------------
// BOOM Config Fragments
// ---------------------

class WithBoomCommitLogPrintf extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      enableCommitLogPrintf = true
    )))
    case other => other
  }
})


class WithBoomBranchPrintf extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      enableBranchPrintf = true
    )))
    case other => other
  }
})

class WithBoomMemtracePrintf extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      enableMemtracePrintf = true
    )))
    case other => other
  }
})

class WithNBoomPerfCounters(n: Int) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      nPerfCounters = n
    )))
    case other => other
  }
})


class WithSynchronousBoomTiles extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      crossingType = SynchronousCrossing()
    ))
    case other => other
  }
})

class WithAsynchronousBoomTiles extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      crossingType = AsynchronousCrossing()
    ))
    case other => other
  }
})

class WithRationalBoomTiles extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      crossingType = RationalCrossing()
    ))
    case other => other
  }
})



/**
 * 1-wide BOOM.
 */
class WithNSmallBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 4,
              decodeWidth = 1,
              numRobEntries = 32,
              issueParams = Seq(
                IssueParams(issueWidth=2, numEntries=8, iqType=IQ_MEM, dispatchWidth=1),
                IssueParams(issueWidth=1, numEntries=8, iqType=IQ_UNQ, dispatchWidth=1),
                IssueParams(issueWidth=1, numEntries=8, iqType=IQ_ALU, dispatchWidth=1),
                IssueParams(issueWidth=1, numEntries=8, iqType=IQ_FP , dispatchWidth=1)),
              numIntPhysRegisters = 52,
              numFpPhysRegisters = 48,
              numIrfReadPorts = 3,
              numFrfReadPorts = 3,
              numFrfBanks = 1,
              numLdqEntries = 8,
              numStqEntries = 8,
              maxBrCount = 8,
              numFetchBufferEntries = 8,
              ftq = FtqParameters(nEntries=16),
              nPerfCounters = 2,
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true))
            ),
            dcache = Some(
              DCacheParams(rowBits = 64, nSets=64, nWays=4, nMSHRs=2, nTLBWays=8)
            ),
            icache = Some(
              ICacheParams(rowBits = 64, nSets=64, nWays=4, fetchBytes=2*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)

/**
 * 2-wide BOOM.
 */
class WithNMediumBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 4,
              decodeWidth = 2,
              numRobEntries = 64,
              issueParams = Seq(
                IssueParams(issueWidth=2, numEntries=12, iqType=IQ_MEM, dispatchWidth=2),
                IssueParams(issueWidth=1, numEntries=12, iqType=IQ_UNQ, dispatchWidth=2),
                IssueParams(issueWidth=2, numEntries=20, iqType=IQ_ALU, dispatchWidth=2),
                IssueParams(issueWidth=1, numEntries=12, iqType=IQ_FP , dispatchWidth=2)),
              numIntPhysRegisters = 80,
              numFpPhysRegisters = 64,
              numIrfReadPorts = 5,
              numIrfBanks = 2,
              numFrfReadPorts = 3,
              numFrfBanks = 1,
              numLdqEntries = 16,
              numStqEntries = 16,
              maxBrCount = 12,
              numFetchBufferEntries = 16,
              ftq = FtqParameters(nEntries=32),
              nPerfCounters = 6,
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true))
            ),
            dcache = Some(
              DCacheParams(rowBits = 64, nSets=64, nWays=4, nMSHRs=2, nTLBWays=8)
            ),
            icache = Some(
              ICacheParams(rowBits = 64, nSets=64, nWays=4, fetchBytes=2*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)
// DOC include start: LargeBoomConfig
/**
 * 3-wide BOOM. Try to match the Cortex-A15.
 */
class WithNLargeBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 8,
              decodeWidth = 3,
              numRobEntries = 96,
              issueParams = Seq(
                IssueParams(issueWidth=2, numEntries=16, iqType=IQ_MEM, dispatchWidth=3),
                IssueParams(issueWidth=1, numEntries=16, iqType=IQ_UNQ, dispatchWidth=3, numSlowEntries=8),
                IssueParams(issueWidth=3, numEntries=16, iqType=IQ_ALU, dispatchWidth=3, numSlowEntries=8),
                IssueParams(issueWidth=1, numEntries=24, iqType=IQ_FP , dispatchWidth=3, numSlowEntries=12)),
              numIntPhysRegisters = 100,
              numFpPhysRegisters = 96,
              numIrfReadPorts = 6,
              numIrfBanks = 2,
              numFrfReadPorts = 3,
              numFrfBanks = 1,
              numLdqEntries = 24,
              numStqEntries = 24,
              maxBrCount = 16,
              numFetchBufferEntries = 24,
              enableColumnALUIssue = true,
              ftq = FtqParameters(nEntries=32),
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true))
            ),
            dcache = Some(
              DCacheParams(rowBits = 128, nSets=64, nWays=8, nMSHRs=4, nTLBWays=16)
            ),
            icache = Some(
              ICacheParams(rowBits = 128, nSets=64, nWays=8, fetchBytes=4*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)
// DOC include end: LargeBoomConfig

class WithNMegaBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 8,
              decodeWidth = 4,
              numRobEntries = 128,
              issueParams = Seq(
                IssueParams(issueWidth=3, numEntries=32, iqType=IQ_MEM, dispatchWidth=4),
                IssueParams(issueWidth=1, numEntries=20, iqType=IQ_UNQ, dispatchWidth=4),
                IssueParams(issueWidth=4, numEntries=40, iqType=IQ_ALU, dispatchWidth=4),
                IssueParams(issueWidth=2, numEntries=32, iqType=IQ_FP , dispatchWidth=4)),
              lsuWidth = 2,
              numIntPhysRegisters = 144,
              numFpPhysRegisters = 128,
              numIrfReadPorts = 4,
              numIrfBanks = 4,
              numFrfReadPorts = 6,
              numFrfBanks = 1,
              numLdqEntries = 32,
              numStqEntries = 32,
              maxBrCount = 20,
              numFetchBufferEntries = 32,
              enablePrefetching = true,
              enableSuperscalarSnapshots = true,
              enableFastLoadUse = true,
              numDCacheBanks = 4,
              ftq = FtqParameters(nEntries=40),
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true))
            ),
            dcache = Some(
              DCacheParams(rowBits = 128, nSets=64, nWays=8, nMSHRs=8, nTLBWays=32)
            ),
            icache = Some(
              ICacheParams(rowBits = 128, nSets=64, nWays=8, fetchBytes=4*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)

class WithNMegaTapeoutBooms(n: Int = 1, singlePorted: Boolean = true) extends Config(
  new WithFastTAGEBPD(singlePorted) ++
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 8,
              decodeWidth = 4,
              numRobEntries = 128,
              issueParams = Seq(
                IssueParams(issueWidth=2, numEntries=32, iqType=IQ_MEM, dispatchWidth=4, numSlowEntries=20),
                IssueParams(issueWidth=1, numEntries=20, iqType=IQ_UNQ, dispatchWidth=4, numSlowEntries=12),
                IssueParams(issueWidth=4, numEntries=20, iqType=IQ_ALU, dispatchWidth=4, numSlowEntries=12),
                IssueParams(issueWidth=2, numEntries=32, iqType=IQ_FP , dispatchWidth=4, numSlowEntries=20)),
              lsuWidth = 2,
              imulLatency = 4,
              numIntPhysRegisters = 144,
              numFpPhysRegisters = 128,
              numIrfReadPorts = 4,
              numIrfBanks = 4,
              numFrfReadPorts = 4,
              numFrfBanks = 4,
              numLdqEntries = 32,
              numStqEntries = 32,
              maxBrCount = 20,
              numFetchBufferEntries = 32,
              enableSlowBTBRedirect = true,
              enablePrefetching = true,
              enableSuperscalarSnapshots = false,
              enableCompactingLSUDuringDispatch = false,
              enableStLdForwarding = false,
              enableAgenStage = true,
              enableColumnALUIssue = true,
              enableALUSingleWideDispatch = true,
              enableBankedFPFreelist = true,
              numDCacheBanks = 4,
              dcacheSinglePorted = singlePorted,
              icacheSinglePorted = singlePorted,
              ftq = FtqParameters(nEntries=40),
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true))
            ),
            dcache = Some(
              DCacheParams(rowBits = 128, nSets=128, nWays=4, nMSHRs=8, nTLBWays=32)
            ),
            icache = Some(
              ICacheParams(rowBits = 128, nSets=64, nWays=8, fetchBytes=4*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)


/**
 * 5-wide BOOM.
  */
class WithNGigaBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 8,
              decodeWidth = 5,
              numRobEntries = 130,
              issueParams = Seq(
                IssueParams(issueWidth=2, numEntries=32, iqType=IQ_MEM, dispatchWidth=5, numSlowEntries=12),
                IssueParams(issueWidth=1, numEntries=32, iqType=IQ_UNQ, dispatchWidth=5, numSlowEntries=24),
                IssueParams(issueWidth=5, numEntries=20, iqType=IQ_ALU, dispatchWidth=5, numSlowEntries=10),
                IssueParams(issueWidth=2, numEntries=32, iqType=IQ_FP , dispatchWidth=5, numSlowEntries=20)),
              lsuWidth = 2,
              numIntPhysRegisters = 128,
              numFpPhysRegisters = 128,
              numFrfReadPorts=6,
              numFrfBanks=1,
              numLdqEntries = 32,
              numStqEntries = 32,
              maxBrCount = 20,
              numFetchBufferEntries = 32,
              enablePrefetching = true,
              enableSuperscalarSnapshots = false,
              enableColumnALUIssue = true,
              numDCacheBanks = 1,
              ftq = FtqParameters(nEntries=40),
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true))
            ),
            dcache = Some(
              DCacheParams(rowBits = 128, nSets=64, nWays=8, nMSHRs=8, nTLBWays=32)
            ),
            icache = Some(
              ICacheParams(rowBits = 128, nSets=64, nWays=8, fetchBytes=4*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)

class WithCloneBoomTiles(
  n: Int = 1,
  cloneTileId: Int = 0,
  location: HierarchicalLocation = InSubsystem,
  cloneLocation: HierarchicalLocation = InSubsystem
) extends Config((site, here, up) => {
  case TilesLocated(`location`) => {
    val prev = up(TilesLocated(location), site)
    val idOffset = up(NumTiles)
    val tileAttachParams = up(TilesLocated(cloneLocation)).find(_.tileParams.tileId == cloneTileId)
      .get.asInstanceOf[BoomTileAttachParams]
    (0 until n).map { i =>
      CloneTileAttachParams(cloneTileId, tileAttachParams.copy(
        tileParams = tileAttachParams.tileParams.copy(tileId = i + idOffset)
      ))
    } ++ prev
  }
  case NumTiles => up(NumTiles) + n
})

/**
  * BOOM Configs for CS152 lab
  */
class WithNCS152BaselineBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        val coreWidth = 1                     // CS152: Change me (1 to 4)
        val lsuWidth  = 1                      // CS152: Change me (1 or 2)
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 4,                   // CS152: Change me (4 or 8)
              numRobEntries = 4,                // CS152: Change me (2+)
              numIntPhysRegisters = 33,         // CS152: Change me (33+)
              numLdqEntries = 8,                // CS152: Change me (2+)
              numStqEntries = 8,                // CS152: Change me (2+)
              maxBrCount = 8,                   // CS152: Change me (2+)
              enableBranchPrediction = false,   // CS152: Change me
              numRasEntries = 0,                // CS152: Change me

              // DO NOT CHANGE BELOW
              enableBranchPrintf = true,
              decodeWidth = coreWidth,
              numFetchBufferEntries = coreWidth * 8,
              numDCacheBanks = lsuWidth,
              lsuWidth = lsuWidth,
              issueParams = Seq(
                IssueParams(issueWidth=2,         numEntries=8,  iqType=IQ_MEM, dispatchWidth=coreWidth),
                IssueParams(issueWidth=1,         numEntries=8,  iqType=IQ_UNQ, dispatchWidth=coreWidth),
                IssueParams(issueWidth=coreWidth, numEntries=16, iqType=IQ_ALU, dispatchWidth=coreWidth),
                IssueParams(issueWidth=1,         numEntries=4,  iqType=IQ_FP , dispatchWidth=coreWidth))
                // DO NOT CHANGE ABOVE
            ),
            dcache = Some(DCacheParams(
              rowBits=64,
              nSets=64, // CS152: Change me (must be pow2, 2-64)
              nWays=4,  // CS152: Change me (1-8)
              nMSHRs=2  // CS152: Change me (1+)
            )),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)

class WithNCS152DefaultBooms(n: Int = 1) extends Config(
  new WithTAGELBPD ++ // Default to TAGE-L BPD
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      val idOffset = up(NumTiles)
      (0 until n).map { i =>
        val coreWidth = 3                     // CS152: Change me (1 to 4)
        val lsuWidth = 1                      // CS152: Change me (1 or 2)
        val nIssueSlots = 32                  // CS152: Change me (2+)
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 4,                   // CS152: Change me (4 or 8)
              numRobEntries = 96,               // CS152: Change me (2+)
              numIntPhysRegisters = 96,         // CS152: Change me (33+)
              numLdqEntries = 16,               // CS152: Change me (2+)
              numStqEntries = 16,               // CS152: Change me (2+)
              maxBrCount = 12,                  // CS152: Change me (2+)
              enableBranchPrediction = true,    // CS152: Change me
              numRasEntries = 16,               // CS152: Change me

              // DO NOT CHANGE BELOW
              enableBranchPrintf = true,
              decodeWidth = coreWidth,
              numFetchBufferEntries = coreWidth * 8,
              numDCacheBanks = lsuWidth,
              lsuWidth = lsuWidth,
              issueParams = Seq(
                IssueParams(issueWidth=2,         numEntries=nIssueSlots, iqType=IQ_MEM, dispatchWidth=coreWidth),
                IssueParams(issueWidth=1,         numEntries=nIssueSlots, iqType=IQ_UNQ, dispatchWidth=coreWidth),
                IssueParams(issueWidth=coreWidth, numEntries=nIssueSlots, iqType=IQ_ALU, dispatchWidth=coreWidth, numSlowEntries=nIssueSlots),
                IssueParams(issueWidth=1,         numEntries=nIssueSlots, iqType=IQ_FP , dispatchWidth=coreWidth))
                // DO NOT CHANGE ABOVE
            ),
            dcache = Some(DCacheParams(
              rowBits=64,
              nSets=64, // CS152: Change me (must be pow2, 2-64)
              nWays=4,  // CS152: Change me (1-8)
              nMSHRs=2  // CS152: Change me (1+)
            )),
            tileId = i + idOffset
          ),
          crossingParams = RocketCrossingParams()
        )
      } ++ prev
    }
    case NumTiles => up(NumTiles) + n
  })
)

/**
  *  Branch prediction configs below
  */

class WithFastTAGEBPD(singlePorted: Boolean = true) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      bpdMaxMetaLength = 120,
      globalHistoryLength = 64,
      localHistoryLength = 1,
      localHistoryNSets = 0,
      branchPredictor = ((resp_in: BranchPredictionBankResponse, p: Parameters) => {
        val tage = Module(new TageBranchPredictorBank(
          BoomTageParams(
            tableInfo = Seq((  128,       2,     8),
                            (  128,       4,     8),
                            (  128,       8,     8),
                            (  128,      16,     8),
                            (  128,      32,     8),
                            (  128,      64,     8)),
            singlePorted = singlePorted
          )
        )(p))
        val slowbtb = Module(new SlowBTBBranchPredictorBank(
          BoomSlowBTBParams(singlePorted = singlePorted))(p))
        val fastbtb = Module(new BTBBranchPredictorBank(
          BoomBTBParams(nSets = 32, nWays = 2, offsetSz = 13, extendedNSets = 32, useFlops = true))(p))
        val slowbim = Module(new BIMBranchPredictorBank(
          BoomBIMParams(nSets = 2048, singlePorted = true, slow = true, nCols = 2))(p))
        val fastbim = Module(new BIMBranchPredictorBank(
          BoomBIMParams(useFlops = true, nSets = 128, singlePorted = false))(p))
        val ubtb = Module(new GMicroBTBBranchPredictorBank(
          BoomGMicroBTBParams())(p))
        val preds = Seq(tage, slowbtb, fastbtb, slowbim, fastbim, ubtb)
        preds.map(_.io := DontCare)

        ubtb.io.resp_in(0)    := resp_in
        fastbim.io.resp_in(0) := ubtb.io.resp
        slowbim.io.resp_in(0) := fastbim.io.resp
        fastbtb.io.resp_in(0) := slowbim.io.resp
        slowbtb.io.resp_in(0) := fastbtb.io.resp
        tage.io.resp_in(0)    := slowbtb.io.resp

        (preds, tage.io.resp)
      })
    )))
    case other => other
  }
})

class WithTAGELBPD extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      bpdMaxMetaLength = 120,
      globalHistoryLength = 64,
      localHistoryLength = 1,
      localHistoryNSets = 0,
      branchPredictor = ((resp_in: BranchPredictionBankResponse, p: Parameters) => {
        val loop = Module(new LoopBranchPredictorBank()(p))
        val tage = Module(new TageBranchPredictorBank()(p))
        val btb = Module(new BTBBranchPredictorBank()(p))
        val bim = Module(new BIMBranchPredictorBank()(p))
        val ubtb = Module(new FA2MicroBTBBranchPredictorBank()(p))
        val preds = Seq(loop, tage, btb, ubtb, bim)
        preds.map(_.io := DontCare)

        ubtb.io.resp_in(0)  := resp_in
        bim.io.resp_in(0)   := ubtb.io.resp
        btb.io.resp_in(0)   := bim.io.resp
        tage.io.resp_in(0)  := btb.io.resp
        loop.io.resp_in(0)  := tage.io.resp

        (preds, loop.io.resp)
      })
    )))
    case other => other
  }
})

class WithBoom2BPD extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      bpdMaxMetaLength = 45,
      globalHistoryLength = 16,
      localHistoryLength = 1,
      localHistoryNSets = 0,
      branchPredictor = ((resp_in: BranchPredictionBankResponse, p: Parameters) => {
        // gshare is just variant of TAGE with 1 table
        val gshare = Module(new TageBranchPredictorBank(
          BoomTageParams(tableInfo = Seq((256, 16, 7)))
        )(p))
        val btb = Module(new BTBBranchPredictorBank()(p))
        val bim = Module(new BIMBranchPredictorBank()(p))
        val preds = Seq(bim, btb, gshare)
        preds.map(_.io := DontCare)

        bim.io.resp_in(0)  := resp_in
        btb.io.resp_in(0)  := bim.io.resp
        gshare.io.resp_in(0) := btb.io.resp
        (preds, gshare.io.resp)
      })
    )))
    case other => other
  }
})

class WithAlpha21264BPD extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      bpdMaxMetaLength = 64,
      globalHistoryLength = 32,
      localHistoryLength = 32,
      localHistoryNSets = 128,
      branchPredictor = ((resp_in: BranchPredictionBankResponse, p: Parameters) => {
        val btb = Module(new BTBBranchPredictorBank()(p))
        val gbim = Module(new HBIMBranchPredictorBank()(p))
        val lbim = Module(new HBIMBranchPredictorBank(BoomHBIMParams(useLocal=true))(p))
        val tourney = Module(new TourneyBranchPredictorBank()(p))
        val preds = Seq(lbim, btb, gbim, tourney)
        preds.map(_.io := DontCare)

        gbim.io.resp_in(0) := resp_in
        lbim.io.resp_in(0) := resp_in
        tourney.io.resp_in(0) := gbim.io.resp
        tourney.io.resp_in(1) := lbim.io.resp
        btb.io.resp_in(0)  := tourney.io.resp

        (preds, btb.io.resp)
      })
    )))
    case other => other
  }
})


class WithSWBPD extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      bpdMaxMetaLength = 1,
      globalHistoryLength = 32,
      localHistoryLength = 1,
      localHistoryNSets = 0,
      branchPredictor = ((resp_in: BranchPredictionBankResponse, p: Parameters) => {
        val sw = Module(new SwBranchPredictorBank()(p))

        sw.io.resp_in(0) := resp_in

        (Seq(sw), sw.io.resp)
      })
    )))
    case other => other
  }
})
