package chipyard.fpga.vc709

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import chipyard.{DigitalTop, DigitalTopModule}

// ------------------------------------
// VC709 DigitalTop
// ------------------------------------

class VC709DigitalTop()(implicit p: Parameters) extends DigitalTop
  // with sifive.blocks.devices.i2c.HasPeripheryI2C
  // with testchipip.HasPeripheryTSIHostWidget
{

  /*** PCIe ***/
  // println("#PCIeOverlayKey = " + p(PCIeOverlayKey).size)
  // topDesign match { case td: ChipTop =>
  //   td.lazySystem match { case lsys: BaseSubsystem =>
  //     println("BaseSubsystem: " + lsys.toString())
  //     p(PCIeOverlayKey).zipWithIndex.map { case (key, i) => 
  //       val overlayOutput = key.place(PCIeDesignInput(wrangler=dutWrangler.node, corePLL=harnessSysPLL)).overlayOutput
  //       val (pcieNode: TLNode, intNode: IntOutwardNode) = (overlayOutput.pcieNode, overlayOutput.intNode)
  //       val (slaveTLNode: TLIdentityNode, masterTLNode: TLAsyncSinkNode) = (pcieNode.inward, pcieNode.outward)
  //       lsys.fbus match { case fbus: FrontBus =>
  //         fbus.coupleFrom(s"master_named_pcie${i}"){ bus =>
  //           (bus
  //             :=* TLFIFOFixer(TLFIFOFixer.all)
  //             :=* masterTLNode)
  //         }
  //       }
  //       lsys.pbus match { case pbus: PeripheryBus =>
  //         pbus.coupleTo(s"slave_named_pcie${i}"){ bus =>
  //           println("pbus: " + bus.toString())
  //           (slaveTLNode
  //             :*= TLWidthWidget(pbus.beatBytes)
  //             :*= bus)
  //         }
  //       }
  //       lsys.ibus match { case ibus: InterruptBusWrapper =>
  //         ibus.fromSync := intNode
  //       }
  //     }
  //   }
  // }
  override lazy val module = new VC709DigitalTopModule(this)
}

class VC709DigitalTopModule[+L <: VC709DigitalTop](l: L) extends DigitalTopModule(l)
  // with sifive.blocks.devices.i2c.HasPeripheryI2CModuleImp
