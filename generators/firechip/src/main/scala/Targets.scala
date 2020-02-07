package firesim.firesim

import chisel3._
import freechips.rocketchip._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.HasPeripheryDebugModuleImp
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.amba.axi4.AXI4Bundle
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.LazyModule
import icenet._
import firesim.util.DefaultFireSimHarness
import testchipip._
import testchipip.SerialAdapter.SERIAL_IF_WIDTH
import tracegen.{HasTraceGenTiles, HasTraceGenTilesModuleImp}
import sifive.blocks.devices.uart._
import java.io.File


object FireSimValName {
  implicit val valName = ValName("FireSimHarness")
}
import FireSimValName._



/*******************************************************************************
* Top level DESIGN configurations. These describe the basic instantiations of
* the designs being simulated.
*
* In general, if you're adding or removing features from any of these, you
* should CREATE A NEW ONE, WITH A NEW NAME. This is because the manager
* will store this name as part of the tags for the AGFI, so that later you can
* reconstruct what is in a particular AGFI. These tags are also used to
* determine which driver to build.
*******************************************************************************/

class FireSimDUT(implicit p: Parameters) extends chipyard.Top
    with HasTraceIO
{
  override lazy val module = new FireSimModuleImp(this)
}

class FireSimModuleImp[+L <: FireSimDUT](l: L) extends chipyard.TopModule(l)
  with HasTraceIOImp
  with CanHaveMultiCycleRegfileImp

class FireSim(implicit p: Parameters) extends DefaultFireSimHarness


class FireSimNoNIC(implicit p: Parameters) extends DefaultFireSimHarness
{
  throw new Exception("FireSimNoNIC is deprecated. Please add WithNoNIC to your TARGET_CONFIG and set DESIGN=FireSim to build a NoNIC simulator")
}


object FireSimTypeAliases {
  // Supernoded-ness comes from setting p(NumNodes) (see DefaultFiresimHarness) to something > 1
  type FireSimSupernode = FireSim

  // Verilog blackbox integration demo
  type FireSimVerilogGCD = FireSim
}
import FireSimTypeAliases._
