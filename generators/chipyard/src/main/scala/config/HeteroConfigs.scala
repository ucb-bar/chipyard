package chipyard

import freechips.rocketchip.config.{Config}

import chipyard.config.{MultiRoCCKey}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{Debug}
import freechips.rocketchip.util.{AsyncResetReg}

import freechips.rocketchip.config.{Parameters, Config, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.{BootROMParams}
import freechips.rocketchip.diplomacy.{SynchronousCrossing, AsynchronousCrossing, RationalCrossing}
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import testchipip._

import hwacha.{Hwacha}
import gemmini._

import boom.common._
import boom.ifu._
import boom.exu._
import boom.lsu._

import chisel3._
import chisel3.util._

// ---------------------
// Heterogenous Configs
// ---------------------

class LargeBoomAndRocketConfig extends Config(
  new boom.common.WithNLargeBooms(1) ++                          // single-core boom
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

// DOC include start: BoomAndRocketWithHwacha
class HwachaLargeBoomAndHwachaRocketConfig extends Config(
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                          // add hwacha to all harts
  new boom.common.WithNLargeBooms(1) ++                      // add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++     // add 1 rocket core
  new chipyard.config.AbstractConfig)
// DOC include end: BoomAndRocketWithHwacha

// DOC include start: DualBoomAndRocketOneHwacha
class LargeBoomAndHwachaRocketConfig extends Config(
  new chipyard.config.WithMultiRoCC ++                                  // support heterogeneous rocc
  new chipyard.config.WithMultiRoCCHwacha(1) ++                         // put hwacha on hart-1 (rocket)
  new hwacha.DefaultHwachaConfig ++                                     // set default hwacha config keys
  new boom.common.WithNLargeBooms(1) ++                                 // add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                // add 1 rocket core
  new chipyard.config.AbstractConfig)
// DOC include end: DualBoomAndRocketOneHwacha


// DOC include start: DualBoomAndRocket
class DualLargeBoomAndDualRocketConfig extends Config(
  new boom.common.WithNLargeBooms(2) ++                   // add 2 boom cores
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++  // add 2 rocket cores
  new chipyard.config.AbstractConfig)
// DOC include end: DualBoomAndRocket

class LargeBoomAndRocketWithControlCoreConfig extends Config(
  new freechips.rocketchip.subsystem.WithNSmallCores(1) ++ // Add a small "control" core
  new boom.common.WithNLargeBooms(1) ++                    // Add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++   // add 1 rocket core
  new chipyard.config.AbstractConfig)

// Replicate BEAGLE

object GemminiConfigs {
  val defaultConfig = GemminiArrayConfig[SInt, SInt](
  // val defaultConfig = GemminiArrayConfig[Float, Float](
    tileRows = 1,
    tileColumns = 1,
    meshRows = 16,
    meshColumns = 16,
    ld_queue_length = 10,
    st_queue_length = 10,
    ex_queue_length = 10,
    rob_entries = 16,
    sp_banks = 4,
    acc_banks = 1,
    sp_capacity = CapacityInKilobytes(256),
    shifter_banks = 1, // TODO add separate parameters for left and up shifter banks
    dataflow = Dataflow.BOTH,
    acc_capacity = CapacityInKilobytes(64),
    mem_pipeline = 1,
    dma_maxbytes = 64, // TODO get this from cacheblockbytes
    dma_buswidth = 128, // TODO get this from SystemBusKey
    aligned_to = 1,
    inputType = SInt(8.W),
    outputType = SInt(19.W),
    accType = SInt(32.W),
    // inputType = Float(8, 24),
    // outputType = Float(8, 24),
    // accType = Float(8, 24),
    // mvin_scale_args = Some(MvinScaleArguments((t: SInt, u: SInt) => t * u, 0, SInt(8.W))),
    // mvin_scale_acc_args = Some(MvinScaleArguments((t: SInt, u: SInt) => t * u, 0, SInt(8.W))),
    mvin_scale_args = None,
    mvin_scale_acc_args = None,
    // mvin_scale_args = Some(MvinScaleArguments((t: Float, u: Float) => t * u, 0, Float(8, 24))),
    // mvin_scale_acc_args = Some(MvinScaleArguments((t: Float, u: Float) => t * u, 0, Float(8, 24))),
    mvin_scale_shared = false,
    pe_latency = 0
  )
}

class WithMultiRoCCSystolic(harts: Int*) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new Gemmini(OpcodeSet.custom3, GemminiConfigs.defaultConfig)).suggestName("systolic_array")
      }))
    }
  }
})

class WithBeagleBOOMs(n: Int = 1, overrideIdOffset: Option[Int] = None) extends Config(
  new WithBoom2BPD ++
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => {
      val prev = up(TilesLocated(InSubsystem), site)
      Seq(
        BoomTileAttachParams(
          tileParams = BoomTileParams(
            core = BoomCoreParams(
              fetchWidth = 8,
              decodeWidth = 3,
              numRobEntries = 21,
              issueParams = Seq(
                IssueParams(issueWidth=1, numEntries=24, iqType=IQT_MEM.litValue, dispatchWidth=3),
                IssueParams(issueWidth=2, numEntries=24, iqType=IQT_INT.litValue, dispatchWidth=3),
                IssueParams(issueWidth=1, numEntries=24, iqType=IQT_FP.litValue , dispatchWidth=3)),
              numIntPhysRegisters = 128,
              numFpPhysRegisters = 96,
              numLdqEntries = 24,
              numStqEntries = 24,
              maxBrCount = 16,
              numFetchBufferEntries = 24,
              ftq = FtqParameters(nEntries=32),
              fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=5, dfmaLatency=5, divSqrt=true)),
              nPerfCounters = 29,
            ),
            dcache = Some(
              DCacheParams(rowBits = site(SystemBusKey).beatBits, nSets=64, nWays=8, nMSHRs=4, nTLBEntries=16)
            ),
            icache = Some(
              ICacheParams(rowBits = site(SystemBusKey).beatBits, nSets=64, nWays=8, fetchBytes=4*4)
            ),
            hartId = 0
          ),
          crossingParams = RocketCrossingParams()
        )
      ) ++ prev
    }
    case SystemBusKey => up(SystemBusKey, site).copy(beatBytes = 16)
    case XLen => 64
  })
)

class WithBoom2BPD extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core = tp.tileParams.core.copy(
      bpdMaxMetaLength = 45,
      globalHistoryLength = 13,
      localHistoryLength = 1,
      localHistoryNSets = 0,
      numRasEntries = 16,
      branchPredictor = ((resp_in: BranchPredictionBankResponse, p: Parameters) => {
        // gshare is just variant of TAGE with 1 table
        val gshare = Module(new TageBranchPredictorBank(
          BoomTageParams(tableInfo = Seq((8192, 13, 7)))
        )(p))
        val btb = Module(new BTBBranchPredictorBank(BoomBTBParams(nSets = 512, nWays=4, extendedNSets = 512))(p))
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

class WithMiniRocketCore(n: Int, overrideIdOffset: Option[Int] = None) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val prev = up(RocketTilesKey, site)
    val small = RocketTileParams(
      name = Some("rocket_tile"),
      core = RocketCoreParams(
        useVM = true,
        mulDiv = Some(MulDivParams(mulUnroll = 8))),
      btb = Some(BTBParams(nEntries = 14, nRAS = 2)),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes),
        nSets = 64,
        nWays = 4)),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes),
        nSets = 64,
        nWays = 4)),
      hartId = 1)
    Seq(small) ++ prev
  }
})

class NewBeagleConfig extends Config(
  new chipyard.config.WithMultiRoCC ++
  new chipyard.config.WithMultiRoCCHwacha(0) ++
  new WithMultiRoCCSystolic(1) ++
  new hwacha.DefaultHwachaConfig ++

  new WithMiniRocketCore(1, Some(1)) ++

  new WithBeagleBOOMs(1, Some(0)) ++

  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 4, capacityKB = 1024) ++
  new chipyard.config.AbstractConfig)
