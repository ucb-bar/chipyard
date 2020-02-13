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
import utilities.{Subsystem, SubsystemModuleImp}
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

class FireSimDUT(implicit p: Parameters) extends Subsystem
    with HasHierarchicalBusTopology
    with CanHaveMasterAXI4MemPort
    with HasPeripheryBootROM
    with CanHavePeripherySerial
    with HasPeripheryUART
    with CanHavePeripheryIceNIC
    with CanHavePeripheryBlockDevice
    with HasTraceIO
{
  override lazy val module = new FireSimModuleImp(this)
}

class FireSimModuleImp[+L <: FireSimDUT](l: L) extends SubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with CanHavePeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryIceNICModuleImpValidOnly
    with CanHavePeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with HasFireSimClockingImp
    with CanHaveMultiCycleRegfileImp

class FireSim(implicit p: Parameters) extends DefaultFireSimHarness(() => new FireSimDUT)

class FireSimNoNICDUT(implicit p: Parameters) extends Subsystem
    with HasHierarchicalBusTopology
    with CanHaveMasterAXI4MemPort
    with HasPeripheryBootROM
    with CanHavePeripherySerial
    with HasPeripheryUART
    with CanHavePeripheryBlockDevice
    with HasTraceIO
{
  override lazy val module = new FireSimNoNICModuleImp(this)
}

class FireSimNoNICModuleImp[+L <: FireSimNoNICDUT](l: L) extends SubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with CanHavePeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with CanHavePeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with HasFireSimClockingImp
    with CanHaveMultiCycleRegfileImp

class FireSimNoNIC(implicit p: Parameters) extends DefaultFireSimHarness(() => new FireSimNoNICDUT)

class FireSimTraceGenDUT(implicit p: Parameters) extends BaseSubsystem
    with HasHierarchicalBusTopology
    with HasTraceGenTiles
    with CanHaveMasterAXI4MemPort {
  override lazy val module = new FireSimTraceGenModuleImp(this)
}

class FireSimTraceGenModuleImp(outer: FireSimTraceGenDUT) extends BaseSubsystemModuleImp(outer)
    with HasTraceGenTilesModuleImp
    with CanHaveMasterAXI4MemPortModuleImp

class FireSimTraceGen(implicit p: Parameters) extends DefaultFireSimHarness(
  () => new FireSimTraceGenDUT)

// Supernoded-ness comes from setting p(NumNodes) (see DefaultFiresimHarness) to something > 1
class FireSimSupernode(implicit p: Parameters) extends DefaultFireSimHarness(() => new FireSimDUT)

// Verilog blackbox integration demo
class FireSimVerilogGCDDUT(implicit p: Parameters) extends FireSimDUT
    with example.CanHavePeripheryGCD
{
  override lazy val module = new FireSimVerilogGCDModuleImp(this)
}

class FireSimVerilogGCDModuleImp[+L <: FireSimVerilogGCDDUT](l: L) extends FireSimModuleImp(l)

class FireSimVerilogGCD(implicit p: Parameters) extends DefaultFireSimHarness(() => new FireSimVerilogGCDDUT)
