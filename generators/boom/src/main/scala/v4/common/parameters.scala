//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package boom.v4.common

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem.{MemoryPortParams}
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.devices.tilelink.{BootROMParams, CLINTParams, PLICParams}

import boom.v4.ifu._
import boom.v4.exu._
import boom.v4.lsu._

/**
 * Default BOOM core parameters
 */
case class BoomCoreParams(
// DOC include start: BOOM Parameters
  pgLevels: Int = 3,
  fetchWidth: Int = 4,
  decodeWidth: Int = 1,
  numRobEntries: Int = 32,
  issueParams: Seq[IssueParams] = Seq(
    IssueParams(issueWidth=2, numEntries=8, iqType=IQ_MEM, dispatchWidth=1),
    IssueParams(issueWidth=1, numEntries=8, iqType=IQ_UNQ, dispatchWidth=1),
    IssueParams(issueWidth=1, numEntries=8, iqType=IQ_ALU, dispatchWidth=1),
    IssueParams(issueWidth=1, numEntries=8, iqType=IQ_FP , dispatchWidth=1)),
  lsuWidth: Int = 1,
  numLdqEntries: Int = 8,
  numStqEntries: Int = 8,
  numIntPhysRegisters: Int = 48,
  numFpPhysRegisters: Int = 48,
  numImmPhysRegisters: Int = 32,
  numIrfReadPorts: Int = 3,
  numFrfReadPorts: Int = 3,
  numIrfBanks: Int = 1,
  numFrfBanks: Int = 1,
  maxBrCount: Int = 4,
  numFetchBufferEntries: Int = 8,
  enableColumnALUIssue: Boolean = false,
  enableALUSingleWideDispatch: Boolean = false,
  enableBankedFPFreelist: Boolean = false,
  enablePrefetching: Boolean = false,
  enableFastLoadUse: Boolean = false,
  enableCompactingLSUDuringDispatch: Boolean = true,
  enableAgenStage: Boolean = false,
  enableStLdForwarding: Boolean = false,
  enableFastPNR: Boolean = false,
  enableSFBOpt: Boolean = true,
  enableGHistStallRepair: Boolean = true,
  enableBTBFastRepair: Boolean = true,
  enableLoadToStoreForwarding: Boolean = true,
  enableSuperscalarSnapshots: Boolean = true,
  enableSlowBTBRedirect: Boolean = false,
  enableBPDHPMs: Boolean = false,

  useAtomicsOnlyForIO: Boolean = false,
  ftq: FtqParameters = FtqParameters(nEntries=16),
  intToFpLatency: Int = 2,
  imulLatency: Int = 3,
  nPerfCounters: Int = 2,
  numRXQEntries: Int = 4,
  numRCQEntries: Int = 8,
  numDCacheBanks: Int = 1,
  dcacheSinglePorted: Boolean = false,

  nPMPs: Int = 8,
  enableICacheDelay: Boolean = false,
  icacheSinglePorted: Boolean = true,

  /* branch prediction */
  enableBranchPrediction: Boolean = true,
  branchPredictor: Function2[BranchPredictionBankResponse, Parameters, Tuple2[Seq[BranchPredictorBank], BranchPredictionBankResponse]] = ((resp_in: BranchPredictionBankResponse, p: Parameters) => (Nil, resp_in)),
  globalHistoryLength: Int = 64,
  localHistoryLength: Int = 32,
  localHistoryNSets: Int = 128,
  bpdMaxMetaLength: Int = 120,
  numRasEntries: Int = 32,
  enableRasTopRepair: Boolean = true,

  /* more stuff */
  useCompressed: Boolean = true,
  useFetchMonitor: Boolean = true,
  bootFreqHz: BigInt = 0,
  fpu: Option[FPUParams] = Some(FPUParams(sfmaLatency=4, dfmaLatency=4, divSqrt=true)),
  usingFPU: Boolean = true,
  haveBasicCounters: Boolean = true,
  misaWritable: Boolean = false,
  mtvecInit: Option[BigInt] = Some(BigInt(0)),
  mtvecWritable: Boolean = true,
  haveCFlush: Boolean = false,
  mulDiv: Option[freechips.rocketchip.rocket.MulDivParams] = Some(MulDivParams(divEarlyOut=true)),
  nBreakpoints: Int = 0, // TODO Fix with better frontend breakpoint unit
  nL2TLBEntries: Int = 512,
  nL2TLBWays: Int = 1,
  nLocalInterrupts: Int = 0,
  useNMI: Boolean = false,
  useAtomics: Boolean = true,
  useDebug: Boolean = true,
  useUser: Boolean = true,
  useSupervisor: Boolean = false,
  useVM: Boolean = true,
  useSCIE: Boolean = false,
  useRVE: Boolean = false,
  useBPWatch: Boolean = false,
  clockGate: Boolean = false,
  mcontextWidth: Int = 0,
  scontextWidth: Int = 0,
  trace: Boolean = false,

  /* debug stuff */
  enableCommitLogPrintf: Boolean = false,
  enableBranchPrintf: Boolean = false,
  enableMemtracePrintf: Boolean = false,

  /* enableConservativeSNI: speculative non-interference */
  enableConservativeSNI: Boolean = false

// DOC include end: BOOM Parameters
) extends freechips.rocketchip.tile.CoreParams
{
  override def traceCustom = Some(new BoomTraceBundle)
  val xLen = 64
  val haveFSDirty = true
  val pmpGranularity: Int = 4
  val instBits: Int = 16
  val lrscCycles: Int = 80 // worst case is 14 mispredicted branches + slop
  val retireWidth = decodeWidth
  val nPTECacheEntries = 0
  val useHypervisor = false
  val jumpInFrontend: Boolean = false // unused in boom
  val traceHasWdata = trace
  val useConditionalZero = false
  val useZba = true
  val useZbb = true
  val useZbs = true

  override def customCSRs(implicit p: Parameters) = new BoomCustomCSRs
}

class BoomTraceBundle extends Bundle {
  val rob_empty = Bool()
}

/**
  * Defines custom BOOM CSRs
  */
class BoomCustomCSRs(implicit p: Parameters) extends freechips.rocketchip.tile.CustomCSRs
  with HasBoomCoreParameters {
  override def chickenCSR = {
    val params = tileParams.core.asInstanceOf[BoomCoreParams]
    val mask = BigInt(
      tileParams.dcache.get.clockGate.toInt << 0 |
      params.clockGate.toInt << 1 |
      params.clockGate.toInt << 2 |
      1 << 3 // Disable OOO when this bit is high. LEGACY, use the enableOOOCSR instead
    )
    val init = BigInt(
      tileParams.dcache.get.clockGate.toInt << 0 |
      params.clockGate.toInt << 1 |
      params.clockGate.toInt << 2 |
      0 << 3 // Enable OOO at init
    )
    Some(CustomCSR(chickenCSRId, mask, Some(init)))
  }

  val enableOOOCSRId = 0x800
  def enableOOOCSR = Some(CustomCSR(enableOOOCSRId, BigInt(1), Some(BigInt(1))))

  val enableBPDCSRId = 0x808
  def enableBPDCSR = Some(CustomCSR(enableBPDCSRId, BigInt(1), Some(BigInt(1))))

  def marchid = CustomCSR.constant(CSRs.marchid, BigInt(2))

  override def decls = enableOOOCSR.toSeq ++ enableBPDCSR.toSeq ++ bpmCSR.toSeq ++ chickenCSR ++ Seq(marchid)
  def enableOOO = getOrElse(enableOOOCSR, _.value(0), true.B) && !getOrElse(chickenCSR, _.value(3), false.B)
  def enableBPD = getOrElse(enableBPDCSR, _.value(0), true.B)
}

/**
 * Mixin trait to add BOOM parameters to expand other traits/objects/etc
 */
trait HasBoomCoreParameters extends freechips.rocketchip.tile.HasCoreParameters
{
  val boomParams: BoomCoreParams = tileParams.core.asInstanceOf[BoomCoreParams]

  //************************************
  // Superscalar Widths

  // fetchWidth provided by CoreParams class.
  // decodeWidth provided by CoreParams class.

  // coreWidth is width of decode, width of integer rename, width of ROB, and commit width
  val coreWidth = decodeWidth

  require (isPow2(fetchWidth))
  require (coreWidth <= fetchWidth)

  //************************************
  // Data Structure Sizes
  val numRobEntries = boomParams.numRobEntries       // number of ROB entries (e.g., 32 entries for R10k)
  val numRxqEntries = boomParams.numRXQEntries       // number of RoCC execute queue entries. Keep small since this holds operands and instruction bits
  val numRcqEntries = boomParams.numRCQEntries       // number of RoCC commit queue entries. This can be large since it just keeps a pdst
  val numLdqEntries = boomParams.numLdqEntries       // number of LAQ entries
  val numStqEntries = boomParams.numStqEntries       // number of SAQ/SDQ entries
  val maxBrCount    = boomParams.maxBrCount          // number of branches we can speculate simultaneously
  val ftqSz         = boomParams.ftq.nEntries        // number of FTQ entries
  val numFetchBufferEntries = boomParams.numFetchBufferEntries // number of instructions that stored between fetch&decode

  val numIntPhysRegs= boomParams.numIntPhysRegisters // size of the integer physical register file
  val numFpPhysRegs = boomParams.numFpPhysRegisters  // size of the floating point physical register file
  val numImmPhysRegs = boomParams.numImmPhysRegisters

  val numIrfReadPorts = boomParams.numIrfReadPorts
  val numIrfBanks = boomParams.numIrfBanks
  val numFrfReadPorts = boomParams.numFrfReadPorts
  val numFrfBanks = boomParams.numFrfBanks

  //************************************
  // Functional Units
  val usingFDivSqrt = boomParams.fpu.isDefined && boomParams.fpu.get.divSqrt

  val mulDivParams = boomParams.mulDiv.getOrElse(MulDivParams())
  val trace = boomParams.trace
  // TODO: Allow RV32IF
  require(!(xLen == 32 && usingFPU), "RV32 does not support fp")

  //************************************
  // Pipelining

  val imulLatency = boomParams.imulLatency
  val dfmaLatency = if (boomParams.fpu.isDefined) boomParams.fpu.get.dfmaLatency else 3
  val sfmaLatency = if (boomParams.fpu.isDefined) boomParams.fpu.get.sfmaLatency else 3
  // All FPU ops padded out to same delay for writeport scheduling.
  require (sfmaLatency == dfmaLatency)

  val intToFpLatency = boomParams.intToFpLatency

  //************************************
  // Issue Units

  val issueParams: Seq[IssueParams] = boomParams.issueParams

  // currently, only support one of each.
  require (issueParams.count(_.iqType == IQ_FP ) == 1 || !usingFPU)
  require (issueParams.count(_.iqType == IQ_MEM) == 1)
  require (issueParams.count(_.iqType == IQ_ALU) == 1)
  require (issueParams.count(_.iqType == IQ_UNQ) == 1)

  val unqIssueParam = issueParams.find(_.iqType == IQ_UNQ).get
  val aluIssueParam = issueParams.find(_.iqType == IQ_ALU).get
  val memIssueParam = issueParams.find(_.iqType == IQ_MEM).get
  val fpIssueParam  = issueParams.find(_.iqType == IQ_FP ).get

  require(unqIssueParam.issueWidth == 1)
  val aluWidth = aluIssueParam.issueWidth
  val memWidth = memIssueParam.issueWidth
  val fpWidth  = fpIssueParam.issueWidth

  val lsuWidth = boomParams.lsuWidth

  require(memWidth >= 2)
  require(memWidth >= lsuWidth)

  issueParams.map(x => require(x.dispatchWidth <= coreWidth && x.dispatchWidth > 0))

  //************************************
  // Load/Store Unit
  val dcacheParams: DCacheParams = tileParams.dcache.get
  val icacheParams: ICacheParams = tileParams.icache.get
  val icBlockBytes = icacheParams.blockBytes

  require(icacheParams.nSets <= 64, "Handling aliases in the ICache is buggy.")

  val enableFastLoadUse = boomParams.enableFastLoadUse
  val enableCompactingLSUDuringDispatch = boomParams.enableCompactingLSUDuringDispatch
  val enableStLdForwarding = boomParams.enableStLdForwarding
  val enableAgenStage = boomParams.enableAgenStage

  val enablePrefetching = boomParams.enablePrefetching
  val nLBEntries = dcacheParams.nMSHRs

  //************************************
  // Branch Prediction
  val globalHistoryLength = boomParams.globalHistoryLength
  val localHistoryLength = boomParams.localHistoryLength
  val localHistoryNSets = boomParams.localHistoryNSets
  val bpdMaxMetaLength = boomParams.bpdMaxMetaLength

  def getBPDComponents(resp_in: BranchPredictionBankResponse, p: Parameters) = {
    boomParams.branchPredictor(resp_in, p)
  }

  val nRasEntries = boomParams.numRasEntries max 2
  val useRAS = boomParams.numRasEntries > 0
  val enableRasTopRepair = boomParams.enableRasTopRepair

  val useBPD = boomParams.enableBranchPrediction
  val useSlowBTBRedirect = boomParams.enableSlowBTBRedirect

  val useLHist = localHistoryNSets > 1 && localHistoryLength > 1

  //************************************
  // Extra Knobs and Features
  val enableFastPNR = boomParams.enableFastPNR
  val enableSFBOpt = boomParams.enableSFBOpt
  val enableGHistStallRepair = boomParams.enableGHistStallRepair
  val enableBTBFastRepair = boomParams.enableBTBFastRepair
  val enableLoadToStoreForwarding = boomParams.enableLoadToStoreForwarding
  val enableSuperscalarSnapshots = boomParams.enableSuperscalarSnapshots
  val enableColumnALUIssue = boomParams.enableColumnALUIssue
  val enableALUSingleWideDispatch = boomParams.enableALUSingleWideDispatch
  require(!enableALUSingleWideDispatch || enableColumnALUIssue)
  val enableColumnALUWrites = boomParams.enableColumnALUIssue && isPow2(aluWidth) && aluWidth > 1
  val dcacheSinglePorted = boomParams.dcacheSinglePorted
  val enableBankedFPFreelist = boomParams.enableBankedFPFreelist
  val enableBPDHPMs = boomParams.enableBPDHPMs

  val enableConservativeSNI = boomParams.enableConservativeSNI


  //************************************
  // Implicitly calculated constants
  val numRobRows      = numRobEntries/coreWidth
  val robAddrSz       = log2Ceil(numRobRows) + log2Ceil(coreWidth)
  // the f-registers are mapped into the space above the x-registers
  val logicalRegCount = if (usingFPU) 64 else 32
  val lregSz          = log2Ceil(logicalRegCount)
  val ipregSz         = log2Ceil(numIntPhysRegs)
  val fpregSz         = log2Ceil(numFpPhysRegs)
  val maxPregSz       = ipregSz max fpregSz
  val immPregSz       = log2Ceil(numImmPhysRegs)
  val ldqAddrSz       = log2Ceil(numLdqEntries)
  val stqAddrSz       = log2Ceil(numStqEntries)
  val lsuAddrSz       = ldqAddrSz max stqAddrSz
  val brTagSz         = log2Ceil(maxBrCount)

  require (numIntPhysRegs >= (32 + coreWidth))
  require (numFpPhysRegs >= (32 + coreWidth))
  require (maxBrCount >=2)
  require (numRobEntries % coreWidth == 0)
  require ((numLdqEntries-1) > coreWidth)
  require ((numStqEntries-1) > coreWidth)

  //***********************************
  // Debug printout parameters
  val COMMIT_LOG_PRINTF   = boomParams.enableCommitLogPrintf // dump commit state, for comparision against ISA sim
  val BRANCH_PRINTF       = boomParams.enableBranchPrintf // dump branch predictor results
  val MEMTRACE_PRINTF     = boomParams.enableMemtracePrintf // dump trace of memory accesses to L1D for debugging

  //************************************
  // Other Non/Should-not-be sythesizable modules
  val useFetchMonitor = boomParams.useFetchMonitor

  //************************************
  // Non-BOOM parameters

  val corePAddrBits = paddrBits
  val corePgIdxBits = pgIdxBits
}
