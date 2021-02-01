package chipyard.fpga.vc709

import chisel3._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.tilelink.{TLInwardNode, TLAsyncCrossingSink}

import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxvcu118mig.{XilinxVC709MIGPads, XilinxVC709MIGParams, XilinxVC709MIG}

case object VC709DDR3Size extends Field[BigInt](0x100000000L) // 4GB
class DualDDR3VC709PlacedOverlay(val shell: VC709Shell, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxVC709MIGPads](name, designInput, shellInput)
{
  val size = p(VC709DDR3Size)

  val migParams = XilinxVC709MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxVC709MIG(migParams))
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
  def ioFactory = new XilinxVC709MIGPads(size)

  InModuleBody {
    ioNode.bundle <> mig.module.io

    // setup async crossing
    asyncSink.module.clock := migClkRstNode.bundle.clock
    asyncSink.module.reset := migClkRstNode.bundle.reset
  }

  shell { InModuleBody {
    require (shell.sys_clock.get.isDefined, "Use of DDRVC709Overlay depends on SysClockVC709PlacedOverlay")
    val (sys, _) = shell.sys_clock.get.get.overlayOutput.node.out(0)
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
    port.sys_clk_i = sys.clock.asUInt
    port.sys_rst = sys.reset // pllReset
    port.aresetn := !ar.reset

    // The pins for Dual DDR3 on vc709 board are emitted in the following order:
    // addr[0->15], ba[0-2], ras_n, cas_n, we_n, reset_n, ck_p, ck_n, cke, cs_n, odt, dm[0->7], dq[0->63], dqs_n[0->7], dqs_p[0->7]
    val allddrpins = Seq(
      "AN19", "AR19", "AP20", "AP17", "AP18", "AJ18", "AN16", "AM16", "AK18", "AK19", "AM17", "AM18", "AL17", "AK17", "AM19", "AL19", // addr[0->15]
      "AR17", "AR18", "AN18", // ba[0->2]
      "AV19", "AT20", "AU19", "BB19", "AT17", "AU17", "AW17", "AV16", "AT16", // ctrl: ras_n, cas_n, we_n, reset_n, ck_p, ck_n, cke, cs_n, odt
      "AT22", "AL22", "AU24", "BB23", "BB12", "AV15", "AK12", "AP13", // dm [0->7]
      "AN24", "AM24", "AR22", "AR23", "AN23", "AM23", "AN21", "AP21", "AK23", "AJ23", "AL21", "AM21", "AJ21", "AJ20", "AK20", "AL20", // dq[0->15]
      "AW22", "AW23", "AW21", "AV21", "AU23", "AV23", "AR24", "AT24", "BB24", "BA24", "AY23", "AY24", "AY25", "BA25", "BB21", "BA21", // dq[16->31]
      "AY14", "AW15", "BB14", "BB13", "AW12", "AY13", "AY12", "BA12", "AU12", "AU13", "AT12", "AU14", "AV13", "AW13", "AT15", "AR15", // dq[32->47]
      "AL15", "AJ15", "AK14", "AJ12", "AJ16", "AL16", "AJ13", "AK13", "AR14", "AT14", "AM12", "AP11", "AM13", "AN13", "AM11", "AN11", // dq[48->63]
      "AP22", "AK22", "AU21", "BB22", "BA14", "AR12", "AL14", "AN14", // dqs_n[0->7]
      "AP23", "AJ22", "AT21", "BA22", "BA15", "AP12", "AK15", "AN15") // dqs_p[0->7]

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.ui_clk))
}
class DualDDR3VC709ShellPlacer(shell: VC709Shell, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[VC709Shell] {
  def place(designInput: DDRDesignInput) = new DualDDR3VC709PlacedOverlay(shell, valName.name, designInput, shellInput)
}