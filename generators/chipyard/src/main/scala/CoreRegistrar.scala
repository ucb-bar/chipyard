package chipyard

import chisel3._

import freechips.rocketchip.config.{Parameters, Config, Field, View}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, RocketCrossingParams}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import ariane.{ArianeTile, ArianeTilesKey, ArianeCrossingKey, ArianeTileParams}

// Third-party core entries
sealed trait CoreRegisterEntryBase {
  type TileParams <: CoreParams
  def tilesKey: Field[Seq[TileParams]]
  def crossingKey: Field[Seq[RocketCrossingParams]]
  def enableTileTrace(site: View, here: View, up: View): PartialFunction[Any, Any]
  def instantiateTile(param: TileParams, crossing: RocketCrossingParams,
    logicalTreeNode: LogicalTreeNode, p: Parameters): Option[BaseTile]
}

class CoreRegisterEntry[TileParamsT <: CoreParams, TileT <: BaseTile](tk: Field[Seq[TileParamsT]], ck: Field[Seq[RocketCrossingParams]],
  tileInstantiator: (TileParamsT, RocketCrossingParams, LookupByHartIdImpl, LogicalTreeNode, Parameters) => TileT) extends CoreRegisterEntryBase {
  type TileParams = TileParamsT
  def tilesKey = tk
  def crossingKey = ck
  def enableTileTrace(site: View, here: View, up: View): PartialFunction[Any, Any] = {
    case in if in == tilesKey => up(this.tilesKey) map (tile => tile.copy(trace = true))
  }
  def instantiateTile(param: TileParams, crossing: RocketCrossingParams, 
    logicalTreeNode: LogicalTreeNode, p: Parameters): Option[BaseTile] = param match {
    case a: TileParams => Some(tileInstantiator(a, crossing, PriorityMuxHartIdFromSeq(p(tilesKey)), logicalTreeNode, p))
    case _ => None
  }
}

object CoreRegistrar {
  val cores: List[CoreRegisterEntryBase] = List(
    // ADD YOUR CORE DEFINITION HERE
    new CoreRegisterEntry[ArianeTileParams, ArianeTile](ArianeTilesKey, ArianeCrossingKey, ((a, b, c, d, p) => {new ArianeTile(a, b, c, d)}))
  )
}