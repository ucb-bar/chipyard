package chipyard.fpga.zcu102

import chisel3._

import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.tilelink.{TLInwardNode, TLAsyncCrossingSink}
import freechips.rocketchip.prci._
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxzcu102mig.{XilinxZCU102MIGPads, XilinxZCU102MIGParams, XilinxZCU102MIG}

class SysClock2ZCU102PlacedOverlay(val shell: ZCU102ShellBasicOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 300, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "AL8")
    shell.xdc.addPackagePin(io.n, "AL7")
    shell.xdc.addIOStandard(io.p, "DIFF_SSTL12")
    shell.xdc.addIOStandard(io.n, "DIFF_SSTL12")
  } }
}
class SysClock2ZCU102ShellPlacer(shell: ZCU102ShellBasicOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[ZCU102ShellBasicOverlays]
{
    def place(designInput: ClockInputDesignInput) = new SysClock2ZCU102PlacedOverlay(shell, valName.name, designInput, shellInput)
}

case object ZCU102DDR2Size extends Field[BigInt](0x40000000L * 2) // 2GB
class DDR2ZCU102PlacedOverlay(val shell: ZCU102FPGATestHarness, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxZCU102MIGPads](name, designInput, shellInput)
{
  val size = p(ZCU102DDRSize)

  val migParams = XilinxZCU102MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxZCU102MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 300) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := designInput.wrangler := ddrUI

  // since this uses a separate clk/rst need to put an async crossing
  val asyncSink = LazyModule(new TLAsyncCrossingSink())
  val migClkRstNode = BundleBridgeSource(() => new Bundle {
    val clock = Output(Clock())
    val reset = Output(Bool())
  })
  val topMigClkRstIONode = shell { migClkRstNode.makeSink() }

  def overlayOutput = DDROverlayOutput(ddr = mig.node)
  def ioFactory = new XilinxZCU102MIGPads(size)

  InModuleBody {
    ioNode.bundle <> mig.module.io

    // setup async crossing
    asyncSink.module.clock := migClkRstNode.bundle.clock
    asyncSink.module.reset := migClkRstNode.bundle.reset
  }

  shell { InModuleBody {
    require (shell.sys_clock2.get.isDefined, "Use of DDRZCU102Overlay depends on SysClock2ZCU102Overlay")
    val (sys, _) = shell.sys_clock2.get.get.overlayOutput.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)

    // connect the async fifo sync to sys_clock2
    topMigClkRstIONode.bundle.clock := sys.clock
    topMigClkRstIONode.bundle.reset := sys.reset

    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.c0_ddr4_ui_clk
    ui.reset := /*!port.mmcm_locked ||*/ port.c0_ddr4_ui_clk_sync_rst
    port.c0_sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset // pllReset
    port.c0_ddr4_aresetn := !(ar.reset.asBool)

    // This was just copied from the SiFive example, but it's hard to follow.
    // The pins are emitted in the following order:
    // adr[0->13], we_n, cas_n, ras_n, bg, ba[0->1], reset_n, act_n, ck_c, ck_t, cke, cs_n, odt, dq[0->63], dqs_c[0->7], dqs_t[0->7], dm_dbi_n[0->7]
    val allddrpins = Seq(

      "AM8", "AM9", "AP8", "AN8", "AK10", "AJ10", "AP9", "AN9", "AP10", "AP11", "AM10", "AL10", "AM11", "AL11",  // adr[0->13]
      "AJ7", "AL5", "AJ9", "AK7", // we_n, cas_n, ras_n, bg
      "AK12", "AJ12", // ba[0->1]
      "AH9", "AK8", "AP7", "AN7", "AM3", "AP2", "AK9", // reset_n, act_n, ck_c, ck_t, cke, cs_n, odt
	  
     // "AK4", "AK5", "AN4", "AM4", "AP4", "AP5", "AM5", "AM6", "AK2", "AK3", "AL1", "AK1", "AN1", "AM1", "AP3", "AN3", // dq[0->15]
     //   "AP6", "AL2", // dqs_c[0->1]
     // "AN6", "AL3", // dqs_t[0->1]
     // "AL6", "AN2") // dm_dbi_n[0->1]

//	val allddrpins2 = Seq( 

      "AK4", "AK5", "AN4", "AM4", "AP4", "AP5", "AM5", "AM6", "AK2", "AK3", "AL1", "AK1", "AN1", "AM1", "AP3", "AN3", // dq[0->15]
      "AP6", "AL2",  // dqs_c[0->1]
      "AN6", "AL3",  // dqs_t[0->1]
      "AL6", "AN2")  // dm_dbi_n[0->1]

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.c0_ddr4_ui_clk))
}

class DDR2ZCU102ShellPlacer(shell: ZCU102FPGATestHarness, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[ZCU102FPGATestHarness] {
  def place(designInput: DDRDesignInput) = new DDR2ZCU102PlacedOverlay(shell, valName.name, designInput, shellInput)
}

