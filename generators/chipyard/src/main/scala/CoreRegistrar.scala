package chipyard

import chisel3._

import freechips.rocketchip.config.{Parameters, Config, Field}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, RocketCrossingParams}
import freechips.rocketchip.devices.tilelink.{BootROMParams}
import freechips.rocketchip.diplomacy.{SynchronousCrossing, AsynchronousCrossing, RationalCrossing}
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import ariane.{ArianeTile, ArianeTilesKey, ArianeCrossingKey, ArianeTileParams}

import chipyard.config.TraceIOMatch

// Third-party core entries
sealed trait CoreRegisterEntryBase {
  type Tile
  type TitleParams
  def tilesKey: Field[Seq[TitleParams]]
  def crossingKey: Field[Seq[RocketCrossingParams]]
}

class CoreRegisterEntry[TileT <: BaseTile, TileParamsT <: CoreParams](tk: Field[Seq[TileParamsT]], ck: Field[Seq[RocketCrossingParams]]) 
  extends CoreRegisterEntryBase with TraceIOMatch {
  type Tile = TileT
  type TileParams = TileParamsT
  def tilesKey = tk
  def crossingKey = ck
}

object CoreRegistrar {
  val cores: List[CoreRegisterEntryBase] = List(
    // ADD YOUR CORE DEFINITION HERE
    new CoreRegisterEntry[ArianeTile, ArianeTileParams](ArianeTilesKey, ArianeCrossingKey)
  )
}