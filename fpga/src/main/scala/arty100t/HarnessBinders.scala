package chipyard.fpga.arty100t

import chisel3._

import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem.{PeripheryBusKey}
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.util.{HeterogeneousBag}

import sifive.blocks.devices.uart.{UARTPortIO, HasPeripheryUARTModuleImp}
import sifive.blocks.devices.jtag.{JTAGPins, JTAGPinsFromPort}
import sifive.blocks.devices.pinctrl.{BasePin}

import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipyard._
import chipyard.harness._
import chipyard.iobinders.JTAGChipIO

import testchipip._

class WithArty100TUARTTSI extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val ath = th.asInstanceOf[Arty100THarness]
      val freq = p(PeripheryBusKey).dtsFrequency.get
      val bits = SerialAdapter.asyncQueue(port, th.buildtopClock, th.buildtopReset)
      withClockAndReset(th.buildtopClock, th.buildtopReset) {
        val ram = SerialAdapter.connectHarnessRAM(system.serdesser.get, bits, th.buildtopReset)
        val uart_to_tsi = Module(new UARTToTSI(freq))
        ram.module.io.tsi_ser.flipConnect(uart_to_tsi.io.serial)

        ath.io_uart_bb.bundle <> uart_to_tsi.io.uart
        ath.other_leds(1) := uart_to_tsi.io.serial.out.valid
        ath.other_leds(2) := uart_to_tsi.io.serial.in.valid
        ath.other_leds(3) := uart_to_tsi.io.uart.rxd
        ath.other_leds(4) := uart_to_tsi.io.uart.txd

        ath.other_leds(9) := ram.module.io.adapter_state(0)
        ath.other_leds(10) := ram.module.io.adapter_state(1)
        ath.other_leds(11) := ram.module.io.adapter_state(2)
        ath.other_leds(12) := ram.module.io.adapter_state(3)
      }
    })
  }
})

class WithArty100TDDRTL extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    require(ports.size == 1)
    val artyTh = th.asInstanceOf[Arty100THarness]
    val bundles = artyTh.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> ports.head
  }
})
