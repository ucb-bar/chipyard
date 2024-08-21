// See LICENSE for license details.

package firechip.bridgeinterfaces

import scala.math.max

import chisel3._
import chisel3.util._

case class BlockDeviceConfig(
  nTrackers: Int = 1
)

trait HasBlockDeviceParameters {
  val bdParams: BlockDeviceConfig
  def dataBytes = 512
  def sectorBits = 32
  def nTrackers = bdParams.nTrackers
  def tagBits = log2Up(nTrackers)
  def nTrackerBits = log2Up(nTrackers+1)
  def dataBitsPerBeat = 64
  def dataBeats = (dataBytes * 8) / dataBitsPerBeat
  def sectorSize = log2Ceil(sectorBits/8)
  def beatIdxBits = log2Ceil(dataBeats)
  def backendQueueDepth = max(2, nTrackers)
  def backendQueueCountBits = log2Ceil(backendQueueDepth+1)
  def pAddrBits = 64 // TODO: make this configurable
}

abstract class BlockDeviceBundle
  extends Bundle with HasBlockDeviceParameters

abstract class BlockDeviceModule
  extends Module with HasBlockDeviceParameters

class BlockDeviceRequest(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val write = Bool()
  val offset = UInt(sectorBits.W)
  val len = UInt(sectorBits.W)
  val tag = UInt(tagBits.W)
}

class BlockDeviceFrontendRequest(bdParams: BlockDeviceConfig)
    extends BlockDeviceRequest(bdParams) {
  val addr = UInt(pAddrBits.W)
}

class BlockDeviceData(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val data = UInt(dataBitsPerBeat.W)
  val tag = UInt(tagBits.W)
}

class BlockDeviceInfo(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val nsectors = UInt(sectorBits.W)
  val max_req_len = UInt(sectorBits.W)
}

class BlockDeviceIO(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val req = Decoupled(new BlockDeviceRequest(bdParams))
  val data = Decoupled(new BlockDeviceData(bdParams))
  val resp = Flipped(Decoupled(new BlockDeviceData(bdParams)))
  val info = Input(new BlockDeviceInfo(bdParams))
}

class BlockDevBridgeTargetIO(bdParams: BlockDeviceConfig) extends Bundle {
  val bdev = Flipped(new BlockDeviceIO(bdParams))
  val reset = Input(Bool())
  val clock = Input(Clock())
}
