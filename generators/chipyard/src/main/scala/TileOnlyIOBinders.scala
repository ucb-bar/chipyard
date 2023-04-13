package chipyard.iobinders

import chisel3._
import chisel3.experimental.{IO, DataMirror}

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.tile.NMI

import chipyard._


class WithTileSlavePort extends OverrideIOBinder({
  println("TileSlaveIOBinder")

  (system: HasTileSlavePort) => {
    val io_slave = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.slave)).suggestName("tile_slave")
    io_slave <> system.slave
// system.slave <> io_slave
    (Seq(io_slave), Nil)
  }
})

class WithTileMasterPort extends OverrideIOBinder({
  println("TileMasterIOBinder")

  (system: HasTileMasterPort) => {
    val io_master = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.master)).suggestName("tile_master")
    io_master <> system.master
    (Seq(io_master), Nil)
  }
})

class WithTileIntSinkPort extends OverrideIOBinder({
  println("TileIntSinkIOBinder")

  (system: HasTileIntSinkPort) => {
    val io_intsink = IO(UInt(1.W)).suggestName("tile_intsink")
    io_intsink <> system.intSink.asUInt
    (Seq(io_intsink), Nil)
  }
})

class WithTileIntSourcePort extends OverrideIOBinder({
  println("TileIntSourceIOBinder")

  (system: HasTileIntSourcePort) => {
    val io_intsource = IO(Flipped(UInt(1.W))).suggestName("tile_intsource")
    system.intSource.asUInt <> io_intsource
    (Seq(io_intsource), Nil)
  }
})

class WithTileHaltSinkPort extends OverrideIOBinder({
  println("TileHaltSinkIOBinder")

  (system: HasTileHaltSinkPort) => {
    val io_halt = IO(UInt(1.W)).suggestName("tile_halt")
    io_halt <> system.halt.asUInt
    (Seq(io_halt), Nil)
  }
})

class WithTileCeaseSinkPort extends OverrideIOBinder({
  println("TileCeaseSinkIOBinder")

  (system: HasTileCeaseSinkPort) => {
    val io_cease = IO(UInt(1.W)).suggestName("tile_cease")
    println(s"cease length ${system.cease.length}")
    io_cease <> system.cease(0).asUInt
    (Seq(io_cease), Nil)
  }
})

class WithTileWFISinkPort extends OverrideIOBinder({
  println("TileWFISinkIOBinder")

  (system: HasTileWFISinkPort) => {
    val io_wfi = IO(UInt(1.W)).suggestName("tile_wfi")
    io_wfi <> system.wfi.asUInt
    (Seq(io_wfi), Nil)
  }
})

class WithTileHartIdSourcePort extends OverrideIOBinder({
  println("TileHartIdSourceIOBinder")

  (system: HasTileHartIdSourcePort) => {
    val io_hartid = IO(Flipped(UInt(2.W))).suggestName("tile_hartid")
    println(s"hartid length ${system.hart.length}")
    system.hart(0) <> io_hartid
    (Seq(io_hartid), Nil)
  }
})

class WithTileResetSourcePort extends OverrideIOBinder({
  println("TileResetSourceIOBinder")

  (system: HasTileResetSourcePort) => {
    val io_resetVec = IO(Flipped(UInt(18.W))).suggestName("tile_resetVec")
    println(system.resetVec.toPrintable)
    println(s"resetVec length ${system.resetVec.length}")
    system.resetVec(0) <> io_resetVec
    (Seq(io_resetVec), Nil)
  }
})

class WithTileNMISourcePort extends OverrideIOBinder({
  println("TileNMISourceIOBinder")

  (system: HasTileNMISourcePort) => {
    val io_nmi = IO(Input(DataMirror.internal.chiselTypeClone[HeterogeneousBag[NMI]](system.nmi))).suggestName("tile_nmi")
    system.nmi <> io_nmi
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
