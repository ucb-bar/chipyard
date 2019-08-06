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
import java.io.File

import memblade.client.{HasPeripheryRemoteMemClient, HasPeripheryRemoteMemClientModuleImpValidOnly}
import memblade.cache.{HasPeripheryDRAMCache, HasPeripheryDRAMCacheModuleImpValidOnly}
import memblade.manager.{HasPeripheryMemBlade, HasPeripheryMemBladeModuleImpValidOnly}

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

class FireSim(implicit p: Parameters) extends RocketSubsystem
    with HasHierarchicalBusTopology
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

class FireSimModuleImp[+L <: FireSim](l: L) extends RocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryIceNICModuleImpValidOnly
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp


class FireSimNoNIC(implicit p: Parameters) extends RocketSubsystem
    with HasHierarchicalBusTopology
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

class FireSimNoNICModuleImp[+L <: FireSimNoNIC](l: L) extends RocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp


class FireBoom(implicit p: Parameters) extends BoomRocketSubsystem
    with HasHierarchicalBusTopology
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

class FireBoomModuleImp[+L <: FireBoom](l: L) extends BoomRocketSubsystemModuleImp(l)
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

class FireBoomNoNIC(implicit p: Parameters) extends BoomRocketSubsystem
    with HasHierarchicalBusTopology
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

class FireBoomNoNICModuleImp[+L <: FireBoomNoNIC](l: L) extends BoomRocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryBlockDeviceModuleImp
    with HasTraceIOImp
    with ExcludeInvalidBoomAssertions

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


class FireSimSupernode(implicit p: Parameters) extends Module {
  val nNodes = p(NumNodes)
  val nodes = Seq.fill(nNodes) {
    Module(LazyModule(new FireSim).module)
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

class FireSimRemoteMemClient(implicit p: Parameters) extends FireSimNoNIC
    with HasPeripheryRemoteMemClient {
  override lazy val module = new FireSimRemoteMemClientModuleImp(this)
}

class FireSimRemoteMemClientModuleImp(outer: FireSimRemoteMemClient)
  extends FireSimNoNICModuleImp(outer)
  with HasPeripheryRemoteMemClientModuleImpValidOnly

class FireSimMemBlade(implicit p: Parameters) extends FireSimNoNIC
    with HasPeripheryMemBlade {
  override lazy val module = new FireSimMemBladeModuleImp(this)
}

class FireSimMemBladeModuleImp(outer: FireSimMemBlade)
  extends FireSimNoNICModuleImp(outer)
  with HasPeripheryMemBladeModuleImpValidOnly

class FireSimDRAMCache(implicit p: Parameters) extends FireSimNoNIC
    with HasPeripheryMemBench
    with HasPeripheryDRAMCache {
  override lazy val module = new FireSimDRAMCacheModuleImp(this)
}

class FireSimDRAMCacheModuleImp(outer: FireSimDRAMCache)
  extends FireSimNoNICModuleImp(outer)
  with HasPeripheryDRAMCacheModuleImpValidOnly

class FireBoomDRAMCache(implicit p: Parameters) extends FireBoomNoNIC
    with HasPeripheryMemBench
    with HasPeripheryDRAMCache {
  override lazy val module = new FireBoomDRAMCacheModuleImp(this)
}

class FireBoomDRAMCacheModuleImp(outer: FireBoomDRAMCache)
  extends FireBoomNoNICModuleImp(outer)
  with HasPeripheryDRAMCacheModuleImpValidOnly
