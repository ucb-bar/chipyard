package chipyard.fpga.vc709

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._

import chipyard.{DigitalTop, DigitalTopModule}

import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

// ------------------------------------
// VC709 DigitalTop
// ------------------------------------

class VC709DigitalTop()(implicit p: Parameters) extends DigitalTop
{
  def dp = p

  /*** The second clock goes to the second DDR ***/
  val memClkNode = dp(ClockInputOverlayKey).last.place(ClockInputDesignInput()).overlayOutput.node
  val harnessMemPLL = dp(PLLFactoryKey)()
  val memGroup = ClockGroup()
  val memWrangler = LazyModule(new ResetWrangler)
  val memClock = ClockSinkNode(freqMHz = dp(FPGAFrequencyKey))
  
  // ClockSinkNode <-- ResetWrangler <-- ClockGroup <-- PLLNode <-- ClockSourceNode
  memClock := memWrangler.node := memGroup := harnessMemPLL := memClkNode

  /*** PCIe dutWrangler.node, harnessSysPLL ***/
  println("#PCIeOverlayKey = " + p(PCIeOverlayKey).size)
  p(PCIeOverlayKey).zipWithIndex.map { case (key, i) => 
    val overlayOutput = key.place(PCIeDesignInput(wrangler=memWrangler.node, corePLL=harnessMemPLL)).overlayOutput
    val (pcieNode: TLNode, intNode: IntOutwardNode) = (overlayOutput.pcieNode, overlayOutput.intNode)
    val (slaveTLNode: TLIdentityNode, masterTLNode: TLAsyncSinkNode) = (pcieNode.inward, pcieNode.outward)
    fbus.coupleFrom(s"master_named_pcie${i}"){ _ :=* TLFIFOFixer(TLFIFOFixer.all) :=* masterTLNode }
    pbus.coupleTo(s"slave_named_pcie${i}"){ slaveTLNode :*= TLWidthWidget(pbus.beatBytes) :*= _ }
    ibus.fromSync := intNode
  }

  override lazy val module = new VC709DigitalTopModule(this)
}

class VC709DigitalTopModule[+L <: VC709DigitalTop](l: L) extends DigitalTopModule(l)