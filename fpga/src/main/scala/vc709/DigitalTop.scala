package chipyard.fpga.vc709

import chisel3._
import chisel3.util._

import freechips.rocketchip.system._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._

import chipyard.{DigitalTop, DigitalTopModule}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pinctrl.{BasePin}

import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

object PinGen {
  def apply(): BasePin = {
    new BasePin()
  }
}

trait HasXilinxVC709PCIe { this: BaseSubsystem =>
  /*** The second clock goes to the PCIe ***/
  val memClkNode = p(ClockInputOverlayKey).last.place(ClockInputDesignInput()).overlayOutput.node
  val harnessMemPLL = p(PLLFactoryKey)()
  val memGroup = ClockGroup()
  val memWrangler = LazyModule(new ResetWrangler)
  val memClock = ClockSinkNode(freqMHz = p(FPGAFrequencyKey))
  
  memClock := memWrangler.node := memGroup := harnessMemPLL := memClkNode

  /*** Instantiate PCIe Module ***/
  p(PCIeOverlayKey).zipWithIndex.map { case (key, i) => 
    val overlayOutput = key.place(PCIeDesignInput(wrangler=memWrangler.node, corePLL=harnessMemPLL)).overlayOutput
    val (pcieNode: TLNode, intNode: IntOutwardNode) = (overlayOutput.pcieNode, overlayOutput.intNode)
    val (slaveTLNode: TLIdentityNode, masterTLNode: TLAsyncSinkNode) = (pcieNode.inward, pcieNode.outward)
    fbus.coupleFrom(s"master_named_pcie${i}"){ _ :=* TLFIFOFixer(TLFIFOFixer.all) :=* masterTLNode }
    pbus.coupleTo(s"slave_named_pcie${i}"){ slaveTLNode :*= TLWidthWidget(pbus.beatBytes) :*= _ }
    ibus.fromSync := intNode
  }
}

trait HasChosenNodeInDTS { this: BaseSubsystem =>
  // Work-around for a kernel bug (command-line ignored if /chosen missing)
  val chosen = new DeviceSnippet {
    def describe() = Description("chosen", Map())
  }
}

// ------------------------------------
// VC709 DigitalTop
// ------------------------------------

// DOC include start: VC709DigitalTop
class VC709DigitalTop()(implicit p: Parameters) extends DigitalTop
  with sifive.blocks.devices.i2c.HasPeripheryI2C // Enables optionally adding the sifive I2C
  with freechips.rocketchip.devices.debug.HasPeripheryDebug
  with HasXilinxVC709PCIe
  with HasChosenNodeInDTS
{
  override lazy val module = new VC709DigitalTopModule(this)
}

class VC709DigitalTopModule[+L <: VC709DigitalTop](l: L) extends DigitalTopModule(l)
  with sifive.blocks.devices.i2c.HasPeripheryI2CModuleImp
  with freechips.rocketchip.devices.debug.HasPeripheryDebugModuleImp
// DOC include end: VC709DigitalTop