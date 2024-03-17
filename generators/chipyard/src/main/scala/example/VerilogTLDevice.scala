package chipyard.example

import chisel3._
import chisel3.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._


// Template for a verilog TileLink device that contains both a register node, and a
// client node
case class VerilogTLDeviceParams(
  deviceName: String,
  moduleName: String,
  verilogResourceFiles: Seq[String],
  ctrlAddress: BigInt = 0x5000,
  ctrlWhere: TLBusWrapperLocation = CBUS,
  clientWhere: TLBusWrapperLocation = SBUS,
  clientIdBits: Int = 4,
  clientBeatBytes: Int = 8,
)

case object VerilogTLDeviceKey extends Field[Option[VerilogTLDeviceParams]](None)

trait CanHavePeripheryVerilogTLDevice { this: BaseSubsystem =>
  p(VerilogTLDeviceKey).map { devParams =>
    // WARNING: This assumes ctrlBus and clientBus are on the same clock
    // This is most likely true, but beware if you start adding
    // clock crossings between buses
    val ctrlBus = locateTLBusWrapper(devParams.ctrlWhere)
    val clientBus = locateTLBusWrapper(devParams.clientWhere)

    val device = LazyModule(new VerilogTLDeviceWrapper(devParams)).suggestName(devParams.deviceName)
    ctrlBus.coupleTo(devParams.deviceName) { device.ctrlNode := TLFragmenter(ctrlBus.beatBytes, ctrlBus.blockBytes) := _ }
    clientBus.coupleFrom(devParams.deviceName) { _ := TLWidthWidget(devParams.clientBeatBytes) := device.clientNode }
    device.clockNode := clientBus.fixedClockNode
  }
}

class VerilogTLDeviceBlackBox(params: VerilogTLDeviceParams, ctrlBundle: TLBundleParameters, clientBundle: TLBundleParameters) extends BlackBox(Map(
  "CTRL_ADDR_BITS"     -> ctrlBundle.addressBits,
  "CTRL_DATA_BITS"     -> ctrlBundle.dataBits,
  "CTRL_SOURCE_BITS"   -> ctrlBundle.sourceBits,
  "CTRL_SINK_BITS"     -> ctrlBundle.sinkBits,
  "CTRL_SIZE_BITS"     -> ctrlBundle.sizeBits,
  "CLIENT_ADDR_BITS"   -> clientBundle.addressBits,
  "CLIENT_DATA_BITS"   -> clientBundle.dataBits,
  "CLIENT_SOURCE_BITS" -> clientBundle.sourceBits,
  "CLIENT_SINK_BITS"   -> clientBundle.sinkBits,
  "CLIENT_SIZE_BITS"   -> clientBundle.sizeBits,
)) with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val tl_ctrl = Flipped(new TLBundle(ctrlBundle))
    val tl_client = new TLBundle(clientBundle)
  })
  override val desiredName = params.moduleName

  require(ctrlBundle.echoFields.isEmpty)
  require(ctrlBundle.requestFields.isEmpty)
  require(ctrlBundle.responseFields.isEmpty)
  require(!ctrlBundle.hasBCE)
  require(clientBundle.echoFields.isEmpty)
  require(clientBundle.requestFields.isEmpty)
  require(clientBundle.responseFields.isEmpty)
  require(!clientBundle.hasBCE)

  params.verilogResourceFiles.foreach(f => addResource(s"/vsrc/$f"))
}


class VerilogTLDeviceWrapper(params: VerilogTLDeviceParams)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice(params.deviceName, Seq(s"ucbbar,${params.deviceName}"))
  val ctrlNode = TLRegisterNode(
    address = Seq(AddressSet(params.ctrlAddress, 4096-1)),
    device = device,
    beatBytes = 8)
  val clientNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = params.deviceName,
    sourceId = IdRange(0, (1 << params.clientIdBits)))))))

  override lazy val module = new VerilogTLDeviceImpl
  class VerilogTLDeviceImpl extends Impl {
    val device = Module(new VerilogTLDeviceBlackBox(params, ctrlNode.in(0)._2.bundle, clientNode.out(0)._2.bundle)).suggestName(params.deviceName)
    device.io.clock := clock
    device.io.reset := reset
    device.io.tl_ctrl <> ctrlNode.in(0)._1
    device.io.tl_client <> clientNode.out(0)._1

  }
}

class WithVerilogTLDevice(params: VerilogTLDeviceParams) extends Config((site, here, up) => {
  case VerilogTLDeviceKey => Some(params)
})
