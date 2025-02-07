package chipyard.example

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem._

case class PeripheralAXI4LiteParams(
  address: BigInt = 0x11000000,
  width: Int = 32,
  useAXI4: Boolean = true)

case object PeripheralDeviceKey extends Field[Option[PeripheralAXI4LiteParams]](None)

class AXI4LiteBundle extends Bundle {
  val s_axi_aclk = Output(Clock())
  val s_axi_aresetn = Output(Bool())

  val s_axi_awaddr = Output(UInt(32.W))
  val s_axi_awvalid = Output(Bool())
  val s_axi_awready = Input(Bool())

  val s_axi_wdata = Output(UInt(32.W))
  val s_axi_wstrb = Output(UInt(4.W))
  val s_axi_wvalid = Output(Bool())
  val s_axi_wready = Input(Bool())

  val s_axi_bresp = Input(UInt(2.W))
  val s_axi_bvalid = Input(Bool())
  val s_axi_bready = Output(Bool())

  val s_axi_araddr = Output(UInt(32.W))
  val s_axi_arvalid = Output(Bool())
  val s_axi_arready = Input(Bool())

  val s_axi_rdata = Input(UInt(32.W))
  val s_axi_rresp = Input(UInt(2.W))
  val s_axi_rvalid = Input(Bool())
  val s_axi_rready = Output(Bool())
}

class PeripheralAXI4Lite(params: PeripheralAXI4LiteParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address = Seq(AddressSet(params.address, 0x01000000-1)), 
      executable = false,
      supportsWrite = TransferSizes(1, beatBytes),
      supportsRead = TransferSizes(1, beatBytes),
      interleavedId = Some(0)
    )),
    beatBytes = beatBytes,
    minLatency = 1
  )))
  override lazy val module = new PeripheralDeviceImpl

  class PeripheralDeviceImpl extends Impl {
    val io = IO(new AXI4LiteBundle())

    withClockAndReset(clock, reset) {
      io.s_axi_aclk := clock
      io.s_axi_aresetn := ~(reset.asBool)
      val (axi_async, _) = node.in(0)
      axi_async.w.ready := io.s_axi_wready

      io.s_axi_awaddr := axi_async.aw.bits.addr - params.address.U
      io.s_axi_awvalid := axi_async.aw.valid
      axi_async.aw.ready := io.s_axi_awready

      io.s_axi_wdata := axi_async.w.bits.data
      io.s_axi_wstrb := axi_async.w.bits.strb
      io.s_axi_wvalid := axi_async.w.valid

      axi_async.b.valid := io.s_axi_bvalid
      axi_async.b.bits.resp := io.s_axi_bresp
      io.s_axi_bready := axi_async.b.ready

      axi_async.ar.ready := io.s_axi_arready
      io.s_axi_araddr := axi_async.ar.bits.addr - params.address.U
      io.s_axi_arvalid := axi_async.ar.valid
      axi_async.ar.bits.id := 0.U

      axi_async.r.valid := io.s_axi_rvalid
      axi_async.r.bits.data := io.s_axi_rdata
      axi_async.r.bits.resp := io.s_axi_rresp
      io.s_axi_rready := axi_async.r.ready
      axi_async.r.bits.last := true.B
    }
  }
}


trait CanHavePeripheralAXI4Lite { this: BaseSubsystem =>
  private val portName = "periph_axi4"
  private val pbus = locateTLBusWrapper(PBUS)

  val peripheral_axi4 = p(PeripheralDeviceKey) match {
    case Some(params) => {
      val numBytes = 4
      val peripheral = LazyModule(new PeripheralAXI4Lite(params, numBytes)(p))
      peripheral.clockNode := pbus.fixedClockNode

      /**
        The chain works from right to left (:= operator connects from right to left):
        - TLBuffer(): Adds pipelining registers on the TileLink side for timing improvement
        - TLWidthWidget: Adapts the data width to match the peripheral bus width
        - TLFragmenter: Breaks down large TileLink transactions into smaller ones (4-byte aligned, up to blockBytes size)
        - TLToAXI4(): Protocol converter from TileLink to AXI4
        - AXI4UserYanker(): Removes user bits from AXI4 transactions (simplifying the protocol)
        - AXI4Buffer(): Adds pipelining registers on the AXI4 side
        - Finally connects to peripheral.node
       */
      pbus.coupleTo(portName) {
        peripheral.node :=
        AXI4Buffer() :=
        AXI4UserYanker() :=
        // AXI4Deinterleaver() :=
        TLToAXI4() :=
        TLFragmenter(numBytes, pbus.blockBytes, holdFirstDeny = true) :=
        TLWidthWidget(pbus.beatBytes) :=
        TLBuffer() := _
      }
      val peripheral_axi4_top = InModuleBody {
        val axi4_io = IO(new AXI4LiteBundle()).suggestName("periph_axi4")
        axi4_io <> peripheral.module.io
        axi4_io
      }
      Some(peripheral_axi4_top)
    }
    case None => None
  }

  
}

class WithPeripheralAXI4Lite(address: BigInt = 0x11000000) extends Config((site, here, up) => {
  case PeripheralDeviceKey => Some(PeripheralAXI4LiteParams(address = address))
})
