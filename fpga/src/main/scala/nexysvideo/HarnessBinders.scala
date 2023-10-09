// See LICENSE for license details.
package chipyard.fpga.nexysvideo

import chisel3._

import freechips.rocketchip.subsystem.{PeripheryBusKey}
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.diplomacy.{LazyRawModuleImp}

import sifive.blocks.devices.uart.{UARTParams}

import chipyard._
import chipyard.harness._

import testchipip._

class WithNexysVideoUARTTSI(uartBaudRate: BigInt = 115200) extends OverrideHarnessBinder({
  (system: CanHavePeripheryUARTTSI, th: HasHarnessInstantiators, ports: Seq[UARTTSIIO]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    require(ports.size <= 1)
    val nexysvideoth = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    ports.map({ port =>
      nexysvideoth.io_uart_bb.bundle <> port.uart
      nexysvideoth.other_leds(1) := port.dropped
      nexysvideoth.other_leds(2) := port.tsi2tl_state(0)
      nexysvideoth.other_leds(3) := port.tsi2tl_state(1)
      nexysvideoth.other_leds(4) := port.tsi2tl_state(2)
      nexysvideoth.other_leds(5) := port.tsi2tl_state(3)
    })
  }
})

class WithNexysVideoDDRTL extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: HasHarnessInstantiators, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    require(ports.size == 1)
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    val bundles = nexysTh.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> ports.head
  }
})
