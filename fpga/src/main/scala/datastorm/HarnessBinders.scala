package chipyard.fpga.datastorm

import chisel3._

import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem.{PeripheryBusKey}
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.diplomacy.{LazyRawModuleImp}
import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import sifive.blocks.devices.uart.{UARTPortIO, UARTParams}
import sifive.blocks.devices.jtag.{JTAGPins, JTAGPinsFromPort}
import sifive.blocks.devices.pinctrl.{BasePin}
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.altera._
import sifive.fpgashells.shell.altera._
import sifive.fpgashells.clocks._
import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import testchipip.serdes._

class WithDatastormDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val bundles = ath.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithDatastormSerialTLToFMC extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io

    harnessIO match {
      case io: DecoupledPhitIO => {
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }
        val packagePinsWithPackageIOs = Seq(
          ("PIN_C13", clkIO),
          ("PIN_J12", IOPin(io.out.valid)),
          ("PIN_K12", IOPin(io.out.ready)),
          ("PIN_H12", IOPin(io.in.valid)),
          ("PIN_H13", IOPin(io.in.ready)),
          ("PIN_E9", IOPin(io.out.bits.phit, 0)),
          ("PIN_D9", IOPin(io.out.bits.phit, 1)),
          ("PIN_H14", IOPin(io.out.bits.phit, 2)),
          ("PIN_G13", IOPin(io.out.bits.phit, 3)),
          ("PIN_C12", IOPin(io.in.bits.phit, 0)),
          ("PIN_B11", IOPin(io.in.bits.phit, 1)),
          ("PIN_E8", IOPin(io.in.bits.phit, 2)),
          ("PIN_D7", IOPin(io.in.bits.phit, 3))
        )
        packagePinsWithPackageIOs foreach { case (pin, io) => {
          ath.io_tcl.addPackagePin(io, pin)
          ath.io_tcl.addIOStandard(io, "1.5 V")
        }}

        ath.sdc.addClock("ser_tl_clock", clkIO, 50)
        ath.sdc.addGroup(clocks = Seq("ser_tl_clock"))
      }
    }
  }
})

class WithDatastormUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("PIN_AG10" , IOPin(harnessIO.rxd)),
      ("PIN_AH9", IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.io_tcl.addPackagePin(io, pin)
      ath.io_tcl.addIOStandard(io, "1.5 V")
    } }
  }
})

// Maps the UART device to the on-board USB-UART
class WithDatastormUART(rxdPin: String = "PIN_AG10", txdPin: String = "PIN_AH9") extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("uart")
    harnessIO <> port.io
    val packagePinsWithPackageIOs = Seq(
      (rxdPin, IOPin(harnessIO.rxd)),
      (txdPin, IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.io_tcl.addPackagePin(io, pin)
      ath.io_tcl.addIOStandard(io, "3.3-V LVTTL")
    } }
  }
})

// Maps the UART device to PMOD JD pins 3/7
class WithDatastormPMODUART extends WithDatastormUART("PIN_AB12", "PIN_AC12")

class WithDatastormJTAG extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("jtag")
    harnessIO <> port.io

    ath.sdc.addClock("JTCK", IOPin(harnessIO.TCK), 10)
    ath.sdc.addGroup(clocks = Seq("JTCK"))
    val packagePinsWithPackageIOs = Seq(
      ("PIN_AD12", IOPin(harnessIO.TCK)),
      ("PIN_AD10", IOPin(harnessIO.TMS)),
      ("PIN_AC9", IOPin(harnessIO.TDI)),
      ("PIN_AD9", IOPin(harnessIO.TDO))
    )
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.io_tcl.addPackagePin(io, pin)
      ath.io_tcl.addIOStandard(io, "3.3-V LVTTL")
      // TODO Check if Cyclone V devices have integrated pullups ath.io_tcl.addPullup(io)
    } }
  }
})