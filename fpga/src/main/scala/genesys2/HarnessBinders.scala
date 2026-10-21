// See LICENSE for license details.
package chipyard.fpga.genesys2

import chipyard.harness._
import chipyard.iobinders._
import chisel3._
import org.chipsalliance.diplomacy.lazymodule.LazyRawModuleImp
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag
import sifive.fpgashells.shell._
import testchipip.serdes._

class WithGenesys2UARTTSI(uartBaudRate: BigInt = 115200) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val genesys2th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    genesys2th.io_uart_bb.bundle <> port.io.uart
    genesys2th.other_leds(1) := port.io.dropped
    genesys2th.other_leds(2) := port.io.tsi2tl_state(0)
    genesys2th.other_leds(3) := port.io.tsi2tl_state(1)
    genesys2th.other_leds(4) := port.io.tsi2tl_state(2)
    genesys2th.other_leds(5) := port.io.tsi2tl_state(3)
  }
})

class WithGenesys2DDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val genesys2Th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    val bundles = genesys2Th.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

// Uses PMOD JA/JB
class WithGenesys2SerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) =>
    val genesys2Th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io

    harnessIO match {
      case io: DecoupledPhitIO =>
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }
        val packagePinsWithPackageIOs = Seq(
          ("U27", clkIO),
          ("U28", IOPin(io.out.valid)),
          ("T26", IOPin(io.out.ready)),
          ("T27", IOPin(io.in.valid)),
          ("T22", IOPin(io.in.ready)),
          ("V29", IOPin(io.out.bits.phit, 0)),
          ("V30", IOPin(io.out.bits.phit, 1)),
          ("V25", IOPin(io.out.bits.phit, 2)),
          ("W26", IOPin(io.out.bits.phit, 3)),
          ("T25", IOPin(io.in.bits.phit, 0)),
          ("U25", IOPin(io.in.bits.phit, 1)),
          ("U22", IOPin(io.in.bits.phit, 2)),
          ("U23", IOPin(io.in.bits.phit, 3))
        )
        packagePinsWithPackageIOs foreach { case (pin, io) =>
          genesys2Th.xdc.addPackagePin(io, pin)
          genesys2Th.xdc.addIOStandard(io, "LVCMOS33")
        }

        // Don't add IOB to the clock, if its an input
        io match {
          case io: DecoupledInternalSyncPhitIO => packagePinsWithPackageIOs foreach { case (pin, io) =>
            genesys2Th.xdc.addIOB(io)
          }
          case io: DecoupledExternalSyncPhitIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) =>
            genesys2Th.xdc.addIOB(io)
          }
        }

        val serialTLFreqMHz = port.params.phyParams match {
          case p: DecoupledInternalSyncSerialPhyParams => p.freqMHz.toDouble
          case p: CreditedSourceSyncSerialPhyParams    => p.freqMHz.toDouble
          case _: DecoupledExternalSyncSerialPhyParams => 100.0
        }
        genesys2Th.sdc.addClock("ser_tl_clock", clkIO, serialTLFreqMHz)
        genesys2Th.sdc.addGroup(pins = Seq(clkIO))
        genesys2Th.xdc.clockDedicatedRouteFalse(clkIO)
    }
})
