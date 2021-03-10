package chipyard.fpga.vc709

import chisel3._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.tilelink.{TLInwardNode, TLAsyncCrossingSink}

import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxvc709mig.{XilinxVC709MIGPads, XilinxVC709MIGParams, XilinxVC709MIG}

class MemClockVC709PlacedOverlay(val shell: VC709ShellBasicOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 233.3333, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "AY18")
    shell.xdc.addPackagePin(io.n, "AY17")
    shell.xdc.addIOStandard(io.p, "DIFF_SSTL15_DCI")
    shell.xdc.addIOStandard(io.n, "DIFF_SSTL15_DCI")
  } }
}
class MemClockVC709ShellPlacer(shell: VC709ShellBasicOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VC709ShellBasicOverlays]
{
    def place(designInput: ClockInputDesignInput) = new MemClockVC709PlacedOverlay(shell, valName.name, designInput, shellInput)
}

case object VC709DDR3Size extends Field[BigInt](0x100000000L) // 4GB
class DualDDR3VC709PlacedOverlay(val shell: VC709FPGATestHarness, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDR3XilinxPlacedOverlay(shell, name, designInput, shellInput)
{
  // since this uses a separate clk/rst need to put an async crossing
  val asyncSink = LazyModule(new TLAsyncCrossingSink())
  val migClkRstNode = BundleBridgeSource(() => new Bundle {
    val clock = Output(Clock())
    val reset = Output(Bool())
  })
  val topMigClkRstIONode = shell { migClkRstNode.makeSink() }

  InModuleBody {
    // setup async crossing
    asyncSink.module.clock := migClkRstNode.bundle.clock
    asyncSink.module.reset := migClkRstNode.bundle.reset
  }

  shell { InModuleBody {
    require (shell.mem_clock.get.isDefined, "Use of DualDDR3VC709PlacedOverlay depends on MemClockVC709PlacedOverlay")

    val (sys, _) = shell.mem_clock.get.get.overlayOutput.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)

    // connect the async fifo sync to sys_clock
    topMigClkRstIONode.bundle.clock := sys.clock
    topMigClkRstIONode.bundle.reset := sys.reset

    val port = topIONode.bundle.port
    io <> port
    // This is modified for vc709
    ui.clock := port.ui_clk
    ui.reset := !port.mmcm_locked || port.ui_clk_sync_rst
    port.sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset // pllReset
    port.aresetn := !ar.reset
  } }

  shell.sdc.addGroup(clocks = Seq("clk_pll_i"))
  // shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.ui_clk))
}
class DualDDR3VC709ShellPlacer(shell: VC709FPGATestHarness, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[VC709FPGATestHarness] {
  def place(designInput: DDRDesignInput) = new DualDDR3VC709PlacedOverlay(shell, valName.name, designInput, shellInput)
}