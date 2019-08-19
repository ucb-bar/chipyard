package firesim.firesim

import chisel3._
import freechips.rocketchip._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.amba.axi4.AXI4Bundle
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.LazyModule
import boom.system.{BoomRocketSubsystem, BoomRocketSubsystemModuleImp}
import icenet._
import testchipip._
import testchipip.SerialAdapter.SERIAL_IF_WIDTH
import sifive.blocks.devices.uart._
import midas.models.AXI4BundleWithEdge
import firesim.util.IOMatchingMIDASEnvironment
import java.io.File


object FireSimValName {
  implicit val valName = ValName("TestHarness")
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

class FireSimDUT(implicit p: Parameters) extends RocketSubsystem
    with HasDefaultBusConfiguration
    with CanHaveFASEDOptimizedMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial
    with HasPeripheryUART
    with HasPeripheryIceNIC
    with HasPeripheryBlockDevice
    with HasTraceIO
{
  override lazy val module = new FireSimModuleImp(this)
}

class FireSimModuleImp[+L <: FireSimDUT](l: L) extends RocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryIceNICModuleImpValidOnly
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with CanHaveRocketMultiCycleRegfileImp

class FireSim(implicit p: Parameters) extends IOMatchingMIDASEnvironment(() => LazyModule(new FireSimDUT).module)

class FireSimNoNICDUT(implicit p: Parameters) extends RocketSubsystem
    with HasDefaultBusConfiguration
    with CanHaveFASEDOptimizedMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial
    with HasPeripheryUART
    with HasPeripheryBlockDevice
    with HasTraceIO
{
  override lazy val module = new FireSimNoNICModuleImp(this)
}

class FireSimNoNICModuleImp[+L <: FireSimNoNICDUT](l: L) extends RocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with CanHaveRocketMultiCycleRegfileImp


class FireSimNoNIC(implicit p: Parameters) extends IOMatchingMIDASEnvironment(() => LazyModule(new FireSimNoNICDUT).module)

class FireBoomDUT(implicit p: Parameters) extends BoomRocketSubsystem
    with HasDefaultBusConfiguration
    with CanHaveFASEDOptimizedMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial
    with HasPeripheryUART
    with HasPeripheryIceNIC
    with HasPeripheryBlockDevice
    with HasTraceIO
{
  override lazy val module = new FireBoomModuleImp(this)
}

class FireBoomModuleImp[+L <: FireBoomDUT](l: L) extends BoomRocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryIceNICModuleImpValidOnly
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with ExcludeInvalidBoomAssertions
    with CanHaveBoomMultiCycleRegfileImp

class FireBoom(implicit p: Parameters) extends IOMatchingMIDASEnvironment(() => LazyModule(new FireBoomDUT).module)

class FireBoomNoNICDUT(implicit p: Parameters) extends BoomRocketSubsystem
    with HasDefaultBusConfiguration
    with CanHaveFASEDOptimizedMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial
    with HasPeripheryUART
    with HasPeripheryBlockDevice
    with HasTraceIO
{
  override lazy val module = new FireBoomNoNICModuleImp(this)
}

class FireBoomNoNICModuleImp[+L <: FireBoomNoNICDUT](l: L) extends BoomRocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with ExcludeInvalidBoomAssertions
    with CanHaveBoomMultiCycleRegfileImp

class FireBoomNoNIC(implicit p: Parameters) extends IOMatchingMIDASEnvironment(() => LazyModule(new FireBoomNoNICDUT).module)

case object NumNodes extends Field[Int]

class SupernodeIO(
      nNodes: Int,
      serialWidth: Int,
      bagPrototype: HeterogeneousBag[AXI4BundleWithEdge])(implicit p: Parameters)
    extends Bundle {

    val serial = Vec(nNodes, new SerialIO(serialWidth))
    val mem_axi = Vec(nNodes, bagPrototype.cloneType)
    val bdev = Vec(nNodes, new BlockDeviceIO)
    val net = Vec(nNodes, new NICIOvonly)
    val uart = Vec(nNodes, new UARTPortIO)

    override def cloneType = new SupernodeIO(nNodes, serialWidth, bagPrototype).asInstanceOf[this.type]
}


class FireSimSupernodeDUT(implicit p: Parameters) extends Module {
  val nNodes = p(NumNodes)
  val nodes = Seq.fill(nNodes) {
    Module(LazyModule(new FireSimDUT).module)
  }

  val io = IO(new SupernodeIO(nNodes, SERIAL_IF_WIDTH, nodes(0).mem_axi4.get))

  io.mem_axi.zip(nodes.map(_.mem_axi4)).foreach {
    case (out, mem_axi4) => out <> mem_axi4.get
  }
  io.serial <> nodes.map(_.serial)
  io.bdev <> nodes.map(_.bdev)
  io.net <> nodes.map(_.net)
  io.uart <> nodes.map(_.uart(0))
  nodes.foreach{ case n => {
    n.debug.clockeddmi.get.dmi.req.valid := false.B
    n.debug.clockeddmi.get.dmi.resp.ready := false.B
    n.debug.clockeddmi.get.dmiClock := clock
    n.debug.clockeddmi.get.dmiReset := reset.toBool
    n.debug.clockeddmi.get.dmi.req.bits.data := DontCare
    n.debug.clockeddmi.get.dmi.req.bits.addr := DontCare
    n.debug.clockeddmi.get.dmi.req.bits.op := DontCare
  } }
}

class FireSimSupernode(implicit p: Parameters) extends IOMatchingMIDASEnvironment(() => new FireSimSupernodeDUT)
