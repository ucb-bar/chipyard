package shuttle.common

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem.{MemoryPortParams}
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.devices.tilelink.{BootROMParams, CLINTParams, PLICParams}

case class ShuttleCoreVectorParams(
  build: Parameters => ShuttleVectorUnit,
  vLen: Int,
  vfLen: Int,
  vfh: Boolean,
  decoder: Parameters => RocketVectorDecoder,
  issueVConfig: Boolean,
  vExts: Seq[String])


case class ShuttleCoreParams(
  nL2TLBEntries: Int = 512,
  nL2TLBWays: Int = 1,

  enableMemALU: Boolean = true,
  enableLateALU: Boolean = true,
  retireWidth: Int = 2,
  fetchWidth: Int = 4,
  debugROB: Boolean = false,
  vector: Option[ShuttleCoreVectorParams] = None,
  enableTraceCoreIngress: Boolean = false
) extends CoreParams
{
  require(Seq(4, 8, 16, 32).contains(fetchWidth))
  override def minFLen: Int = 16
  val xLen = 64
  val pgLevels = 3
  val bootFreqHz: BigInt = 0
  val decodeWidth: Int = fetchWidth
  val fpu: Option[freechips.rocketchip.tile.FPUParams] = Some(FPUParams(minFLen = 16,
    sfmaLatency=4, dfmaLatency=4, divSqrt=true))
  val haveBasicCounters: Boolean = true
  val haveCFlush: Boolean = false
  val haveFSDirty: Boolean = true
  val instBits: Int = 16
  def lrscCycles: Int = 30
  val mcontextWidth: Int = 0
  val misaWritable: Boolean = false
  val mtvecInit: Option[BigInt] = Some(BigInt(0))
  val mtvecWritable: Boolean = true
  val mulDiv: Option[freechips.rocketchip.rocket.MulDivParams] = Some(MulDivParams(mulUnroll=0, divEarlyOut=true))
  val nBreakpoints: Int = 0
  val nLocalInterrupts: Int = 0
  val nPMPs: Int = 0
  val nPerfCounters: Int = 0
  val pmpGranularity: Int = 4
  val scontextWidth: Int = 0
  val useAtomics: Boolean = true
  val useAtomicsOnlyForIO: Boolean = false
  val useBPWatch: Boolean = false
  val useCompressed: Boolean = true
  val useDebug: Boolean = true
  val useNMI: Boolean = false
  val useRVE: Boolean = false
  val useSCIE: Boolean = false
  val useSupervisor: Boolean = false
  val useUser: Boolean = false
  val useVM: Boolean = true
  val nPTECacheEntries: Int = 0
  val useHypervisor: Boolean = false
  val useConditionalZero = false
  val useZba = true
  val useZbb = true
  val useZbs = true
  override val useVector = vector.isDefined
  override def vLen = vector.map(_.vLen).getOrElse(0)
  override def eLen = 64
  override def vfLen = vector.map(_.vfLen).getOrElse(0)
  override def vfh = vector.map(_.vfh).getOrElse(false)
  override def vExts = vector.map(_.vExts).getOrElse(Nil)
  val traceHasWdata: Boolean = debugROB
}
