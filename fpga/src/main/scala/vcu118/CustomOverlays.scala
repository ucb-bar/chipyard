package chipyard.fpga.vcu118

import chisel3._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.tilelink.{TLInwardNode, TLAsyncCrossingSink}

import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxvcu118mig.{XilinxVCU118MIGPads, XilinxVCU118MIGParams, XilinxVCU118MIG}

class SysClock2VCU118PlacedOverlay(val shell: VCU118ShellBasicOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 250, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "AW26")
    shell.xdc.addPackagePin(io.n, "AW27")
    shell.xdc.addIOStandard(io.p, "DIFF_SSTL12")
    shell.xdc.addIOStandard(io.n, "DIFF_SSTL12")
  } }
}
class SysClock2VCU118ShellPlacer(shell: VCU118ShellBasicOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VCU118ShellBasicOverlays]
{
    def place(designInput: ClockInputDesignInput) = new SysClock2VCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

case object VCU118DDR2Size extends Field[BigInt](0x40000000L * 2) // 2GB
class DDR2VCU118PlacedOverlay(val shell: VCU118FPGATestHarness, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxVCU118MIGPads](name, designInput, shellInput)
{
  val size = p(VCU118DDRSize)

  val migParams = XilinxVCU118MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxVCU118MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
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
  def ioFactory = new XilinxVCU118MIGPads(size)

  InModuleBody {
    ioNode.bundle <> mig.module.io

    // setup async crossing
    asyncSink.module.clock := migClkRstNode.bundle.clock
    asyncSink.module.reset := migClkRstNode.bundle.reset
  }

  shell { InModuleBody {
    require (shell.sys_clock2.get.isDefined, "Use of DDRVCU118Overlay depends on SysClock2VCU118Overlay")
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
    port.c0_ddr4_aresetn := !ar.reset

    // This was just copied from the SiFive example, but it's hard to follow.
    // The pins are emitted in the following order:
    // adr[0->13], we_n, cas_n, ras_n, bg, ba[0->1], reset_n, act_n, ck_c, ck_t, cke, cs_n, odt, dq[0->63], dqs_c[0->7], dqs_t[0->7], dm_dbi_n[0->7]
    val allddrpins = Seq(
      "AM27", "AL27", "AP26", "AP25", "AN28", "AM28", "AP28", "AP27", "AN26", "AM26", "AR28", "AR27", "AV25", "AT25", // adr[0->13]
      "AV28", "AU26", "AV26", "AU27", // we_n, cas_n, ras_n, bg
      "AR25", "AU28", // ba[0->1]
      "BD35", "AN25", "AT27", "AT26", "AW28", "AY29", "BB29", // reset_n, act_n, ck_c, ck_t, cke, cs_n, odt
      "BD30", "BE30", "BD32", "BE33", "BC33", "BD33", "BC31", "BD31", "BA32", "BB33", "BA30", "BA31", "AW31", "AW32", "AY32", "AY33", // dq[0->15]
      "AV30", "AW30", "AU33", "AU34", "AT31", "AU32", "AU31", "AV31", "AR33", "AT34", "AT29", "AT30", "AP30", "AR30", "AN30", "AN31", // dq[16->31]
      "BE34", "BF34", "BC35", "BC36", "BD36", "BE37", "BF36", "BF37", "BD37", "BE38", "BC39", "BD40", "BB38", "BB39", "BC38", "BD38", // dq[32->47]
      "BB36", "BB37", "BA39", "BA40", "AW40", "AY40", "AY38", "AY39", "AW35", "AW36", "AU40", "AV40", "AU38", "AU39", "AV38", "AV39", // dq[48->63]
      "BF31", "BA34", "AV29", "AP32", "BF35", "BF39", "BA36", "AW38", // dqs_c[0->7]
      "BF30", "AY34", "AU29", "AP31", "BE35", "BE39", "BA35", "AW37", // dqs_t[0->7]
      "BE32", "BB31", "AV33", "AR32", "BC34", "BE40", "AY37", "AV35") // dm_dbi_n[0->7]

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.c0_ddr4_ui_clk))
}

class DDR2VCU118ShellPlacer(shell: VCU118FPGATestHarness, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[VCU118FPGATestHarness] {
  def place(designInput: DDRDesignInput) = new DDR2VCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}
