package chipyard.iobinders

import chisel3._
import chisel3.experimental.{Analog, IO, DataMirror}

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.groundtest.{GroundTestSubsystemModuleImp, GroundTestSubsystem}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import tracegen.{TraceGenSystemModuleImp}

import barstools.iocell.chisel._

import testchipip._
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}
import chipyard.{CanHaveMasterTLMemPort}
import chipyard.clocking.{HasChipyardPRCI, DividerOnlyClockGenerator}

import chipyard._
import freechips.rocketchip.tile.NMI
import scala.reflect.{ClassTag}
import IOBinderTypes._


class WithTileSlavePort extends OverrideIOBinder({
  (system: HasTileSlavePort) => {
    val io_slave = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.slave)).suggestName("tile_slave")
    io_slave <> system.slave
    (Seq(io_slave), Nil)
  }
})

class WithTileMasterPort extends OverrideIOBinder({
  (system: HasTileMasterPort) => {
    val io_master = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.master)).suggestName("tile_master")
    io_master <> system.master
    (Seq(io_master), Nil)
  }
})


class WithTileIntSinkPort extends OverrideIOBinder({
  (system: HasTileIntSinkPort) => {
    val io_intsink = IO(UInt(1.W)).suggestName("tile_intsink")
    (Seq(io_intsink), Nil)
  }
})

class WithTileIntSourcePort extends OverrideIOBinder({
  (system: HasTileIntSourcePort) => {
    val io_intsource = IO(UInt(1.W)).suggestName("tile_intsource")
    (Seq(io_intsource), Nil)
  }
})

class WithTileHaltSinkPort extends OverrideIOBinder({
  (system: HasTileHaltSinkPort) => {
    val io_halt = IO(UInt(1.W)).suggestName("tile_halt")
    (Seq(io_halt), Nil)
  }
})

class WithTileCeaseSinkPort extends OverrideIOBinder({
  (system: HasTileCeaseSinkPort) => {
    val io_cease = IO(UInt(1.W)).suggestName("tile_cease")
    (Seq(io_cease), Nil)
  }
})

class WithTileWFISinkPort extends OverrideIOBinder({
  (system: HasTileWFISinkPort) => {
    val io_wfi = IO(UInt(1.W)).suggestName("tile_wfi")
    (Seq(io_wfi), Nil)
  }
})

class WithTileHartIdSourcePort extends OverrideIOBinder({
  (system: HasTileHaltSinkPort) => {
    val io_hartid = IO(UInt(2.W)).suggestName("tile_hartid")
    (Seq(io_hartid), Nil)
  }
})

class WithTileResetSourcePort extends OverrideIOBinder({
  (system: HasTileResetSourcePort) => {
    val io_resetVec = IO(UInt(18.W)).suggestName("tile_resetVec")
    (Seq(io_resetVec), Nil)
  }
})

class WithTileNMISourcePort extends OverrideIOBinder({
  (system: HasTileNMISourcePort) => {
    val io_nmi = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[NMI]](system.nmi)).suggestName("tile_nmi")
    (Seq(io_nmi), Nil)
  }
})

class WithTileOnlyIOBinders extends Config({
  new WithTileNMISourcePort ++
  new WithTileResetSourcePort ++
  new WithTileHartIdSourcePort ++
  new WithTileWFISinkPort ++
  new WithTileCeaseSinkPort ++
  new WithTileHaltSinkPort ++
  new WithTileIntSourcePort ++
  new WithTileIntSinkPort ++
  new WithTileMasterPort ++
  new WithTileSlavePort
})