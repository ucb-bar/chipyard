package chipyard.harness

import chisel3._
import chisel3.util._
import chisel3.reflect.DataMirror
import chisel3.experimental.Direction

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.devices.debug.{SimJTAG}
import chipyard.iocell._
import testchipip.dram.{SimDRAM}
import testchipip.tsi.{SimTSI, SerialRAM, TSI, TSIIO}
import testchipip.soc.{TestchipSimDTM}
import testchipip.spi.{SimSPIFlashModel}
import testchipip.uart.{UARTAdapter, UARTToSerial}
import testchipip.serdes._
import testchipip.iceblk.{SimBlockDevice, BlockDeviceModel}
import testchipip.cosim.{SpikeCosim}
import icenet.{NicLoopback, SimNetwork}
import chipyard._
import chipyard.clocking.{HasChipyardPRCI}
import chipyard.iobinders._

case object HarnessBinders extends Field[HarnessBinderFunction]({case _ => })

object ApplyHarnessBinders {
  def apply(th: HasHarnessInstantiators, ports: Seq[Port[_]], chipId: Int)(implicit p: Parameters): Unit = {
    ports.foreach(port => p(HarnessBinders)(th, port, chipId))
  }
}

class HarnessBinder[T <: HasHarnessInstantiators, S <: Port[_]](
  fn: => HarnessBinderFunction
) extends Config((site, here, up) => {
  case HarnessBinders => fn orElse up(HarnessBinders)
})


class WithGPIOTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPort, chipId: Int) => {
    port.io <> AnalogConst(0)

  }
})

class WithGPIOPinsTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPinsPort, chipId: Int) => {
    port.io := DontCare

  }
})

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val div = (th.getHarnessBinderClockFreqMHz.toDouble * 1000000 / port.io.c.initBaudRate.toDouble).toInt
    UARTAdapter.connect(Seq(port.io), div, false)
  }
})
// DOC include end: WithUARTAdapter

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SPIFlashPort, chipId: Int) => {
    val spi_mem = Module(new SimSPIFlashModel(port.params.fSize, port.spiId, rdOnly)).suggestName(s"spi_mem${port.spiId}")
    spi_mem.io.sck := port.io.sck
    require(port.params.csWidth == 1, "I don't know what to do with your extra CS bits. Fix me please.")
    spi_mem.io.cs(0) := port.io.cs(0)
    spi_mem.io.dq.zip(port.io.dq).foreach { case (x, y) => x <> y }
    spi_mem.io.reset := th.harnessBinderReset
  }
})

class WithI2CTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: I2CPort, chipId: Int) => {
    port.io <> DontCare
  }
})

class WithSimBlockDevice extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: BlockDevicePort, chipId: Int) => {
    val sim_blkdev = Module(new SimBlockDevice(port.params))
    sim_blkdev.io.bdev <> port.io.bits
    sim_blkdev.io.clock := port.io.clock
    sim_blkdev.io.reset := th.harnessBinderReset
  }
})

class WithBlockDeviceModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: BlockDevicePort, chipId: Int) => {
    val blkdev_model = Module(new BlockDeviceModel(16, port.params))
    blkdev_model.io <> port.io.bits
    blkdev_model.clock := port.io.clock
    blkdev_model.reset := th.harnessBinderReset
  }
})

class WithLoopbackNIC extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: NICPort, chipId: Int) => {
    withClock(port.io.clock) { NicLoopback.connect(port.io.bits, port.params) }
  }
})

class WithSimNetwork extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: NICPort, chipId: Int) => {
    withClock(port.io.clock) { SimNetwork.connect(Some(port.io.bits), port.io.clock, th.harnessBinderReset.asBool) }
  }
})

class WithSimAXIMem extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4MemPort, chipId: Int) => {
    val mem = LazyModule(new SimAXIMem(port.edge, size=port.params.master.size)(Parameters.empty))
    withClock(port.io.clock) { Module(mem.module) }
    mem.io_axi4.head <> port.io.bits
  }
})

class WithBlackBoxSimMem(additionalLatency: Int = 0) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4MemPort, chipId: Int) => {
    // TODO FIX: This currently makes each SimDRAM contain the entire memory space
    val memSize = port.params.master.size
    val memBase = port.params.master.base
    val lineSize = 64 // cache block size
    val clockFreq = port.clockFreqMHz
    val mem = Module(new SimDRAM(memSize, lineSize, clockFreq, memBase, port.edge.bundle, chipId)).suggestName("simdram")

    mem.io.clock := port.io.clock
    mem.io.reset := th.harnessBinderReset.asAsyncReset
    mem.io.axi <> port.io.bits
    // Bug in Chisel implementation. See https://github.com/chipsalliance/chisel3/pull/1781
    def Decoupled[T <: Data](irr: IrrevocableIO[T]): DecoupledIO[T] = {
      require(DataMirror.directionOf(irr.bits) == Direction.Output, "Only safe to cast produced Irrevocable bits to Decoupled.")
      val d = Wire(new DecoupledIO(chiselTypeOf(irr.bits)))
      d.bits := irr.bits
      d.valid := irr.valid
      irr.ready := d.ready
      d
    }
    if (additionalLatency > 0) {
      withClock (port.io.clock) {
        mem.io.axi.aw  <> (0 until additionalLatency).foldLeft(Decoupled(port.io.bits.aw))((t, _) => Queue(t, 1, pipe=true))
        mem.io.axi.w   <> (0 until additionalLatency).foldLeft(Decoupled(port.io.bits.w ))((t, _) => Queue(t, 1, pipe=true))
        port.io.bits.b <> (0 until additionalLatency).foldLeft(Decoupled(mem.io.axi.b   ))((t, _) => Queue(t, 1, pipe=true))
        mem.io.axi.ar  <> (0 until additionalLatency).foldLeft(Decoupled(port.io.bits.ar))((t, _) => Queue(t, 1, pipe=true))
        port.io.bits.r <> (0 until additionalLatency).foldLeft(Decoupled(mem.io.axi.r   ))((t, _) => Queue(t, 1, pipe=true))
      }
    }
  }
})

class WithSimAXIMMIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4MMIOPort, chipId: Int) => {
    val mmio_mem = LazyModule(new SimAXIMem(port.edge, size = port.params.size)(Parameters.empty))
    withClock(port.io.clock) { Module(mmio_mem.module).suggestName("mmio_mem") }
    mmio_mem.io_axi4.head <> port.io.bits
  }
})

class WithTieOffInterrupts extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ExtIntPort, chipId: Int) => {
    port.io := 0.U
  }
})

class WithTieOffL2FBusAXI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4InPort, chipId: Int) => {
    port.io := DontCare
    port.io.bits.aw.valid := false.B
    port.io.bits.w.valid := false.B
    port.io.bits.b.ready := false.B
    port.io.bits.ar.valid := false.B
    port.io.bits.r.ready := false.B
  }
})

class WithSimJTAGDebug extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val dtm_success = WireInit(false.B)
    when (dtm_success) { th.success := true.B }
    val jtag_wire = Wire(new JTAGIO)
    jtag_wire.TDO.data := port.io.TDO
    jtag_wire.TDO.driven := true.B
    port.io.TCK := jtag_wire.TCK
    port.io.TMS := jtag_wire.TMS
    port.io.TDI := jtag_wire.TDI
    port.io.reset.foreach(_ := th.harnessBinderReset.asBool)
    val jtag = Module(new SimJTAG(tickDelay=3))
    jtag.connect(jtag_wire, th.harnessBinderClock, th.harnessBinderReset.asBool, ~(th.harnessBinderReset.asBool), dtm_success)
  }
})

class WithSimDMI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: DMIPort, chipId: Int) => {
    val dtm_success = WireInit(false.B)
    when (dtm_success) { th.success := true.B }
    val dtm = Module(new TestchipSimDTM()(Parameters.empty)).connect(th.harnessBinderClock, th.harnessBinderReset.asBool, port.io, dtm_success)
  }
})

class WithTiedOffJTAG extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    port.io.TCK := true.B.asClock
    port.io.TMS := true.B
    port.io.TDI := true.B
    port.io.reset.foreach(_ := true.B)
  }
})

class WithTiedOffDMI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: DMIPort, chipId: Int) => {
    port.io.dmi.req.valid := false.B
    port.io.dmi.req.bits := DontCare
    port.io.dmi.resp.ready := true.B
    port.io.dmiClock := false.B.asClock
    port.io.dmiReset := true.B
  }
})

// If tieoffs is specified, a list of serial portIds to tie off
// If tieoffs is unspecified, ties off all serial ports
class WithSerialTLTiedOff(tieoffs: Option[Seq[Int]] = None) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) if (tieoffs.map(_.contains(port.portId)).getOrElse(true)) => {
    port.io match {
      case io: DecoupledPhitIO => io.out.ready := false.B; io.in.valid := false.B; io.in.bits := DontCare;
      case io: CreditedSourceSyncPhitIO => {
        io.clock_in := false.B.asClock
        io.reset_in := false.B.asAsyncReset
        io.in := DontCare
      }
    }
    port.io match {
      case io: HasClockOut =>
      case io: HasClockIn => io.clock_in := false.B.asClock
      case io: CreditedSourceSyncPhitIO =>
      case _ =>
    }
  }
})

class WithSimTSIOverSerialTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) if (port.portId == 0) => {
    port.io match {
      case io: HasClockOut =>
      case io: HasClockIn => io.clock_in := th.harnessBinderClock
      case io: CreditedSourceSyncPhitIO => io.clock_in := th.harnessBinderClock; io.reset_in := th.harnessBinderReset
    }

    port.io match {
      case io: DecoupledPhitIO => {
        // If the port is locally synchronous (provides a clock), drive everything with that clock
        // Else, drive everything with the harnes clock
        val clock = port.io match {
          case io: HasClockOut => io.clock_out
          case io: HasClockIn => th.harnessBinderClock
        }
        withClock(clock) {
          val ram = Module(LazyModule(new SerialRAM(port.serdesser, port.params)(port.serdesser.p)).module)
          ram.io.ser.in <> io.out
          io.in <> ram.io.ser.out

          val success = SimTSI.connect(ram.io.tsi, clock, th.harnessBinderReset, chipId)
          when (success) { th.success := true.B }
        }
      }
    }
  }
})

class WithDriveChipIdPin extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ChipIdPort, chipId: Int) => {
    require(chipId < math.pow(2, port.io.getWidth), "ID Pin is not wide enough")
    port.io := chipId.U
  }
})

class WithSimUARTToUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    UARTAdapter.connect(Seq(port.io.uart),
      baudrate=port.io.uart.c.initBaudRate,
      clockFrequency=th.getHarnessBinderClockFreqHz.toInt,
      forcePty=true)
  }
})

class WithSimTSIToUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val freq = th.getHarnessBinderClockFreqHz.toInt
    val uart_to_serial = Module(new UARTToSerial(freq, port.io.uart.c))
    val serial_width_adapter = Module(new SerialWidthAdapter(8, TSI.WIDTH))
    val success = SimTSI.connect(Some(TSIIO(serial_width_adapter.io.wide)), th.harnessBinderClock, th.harnessBinderReset)
    when (success) { th.success := true.B }
    assert(!uart_to_serial.io.dropped)
    serial_width_adapter.io.narrow.flipConnect(uart_to_serial.io.serial)
    uart_to_serial.io.uart.rxd := port.io.uart.txd
    port.io.uart.rxd := uart_to_serial.io.uart.txd
  }
})

class WithTraceGenSuccess extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SuccessPort, chipId: Int) => {
    when (port.io) { th.success := true.B }
  }
})

class WithCospike extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TracePort, chipId: Int) => {
    port.io.traces.zipWithIndex.map(t => SpikeCosim(t._1, t._2, port.cosimCfg))
  }
})


class WithCustomBootPinPlusArg extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: CustomBootPort, chipId: Int) => {
    val pin = PlusArg("custom_boot_pin", width=1)
    port.io := pin
  }
})

class WithClockFromHarness extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ClockPort, chipId: Int) => {
// DOC include start: HarnessClockInstantiatorEx
    port.io := th.harnessClockInstantiator.requestClockMHz(s"clock_${port.freqMHz}MHz", port.freqMHz)
// DOC include end: HarnessClockInstantiatorEx
  }
})

class WithResetFromHarness extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ResetPort, chipId: Int) => {
    port.io := th.referenceReset.asAsyncReset
  }
})

class WithOffchipBusSelPlusArg extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: OffchipSelPort, chipId: Int) => {
    val pin = PlusArg("offchip_sel", width=port.io.getWidth)
    port.io := pin
  }
})

