package chipyard

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

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

  def findTilesWithFilter(view: View, p: Any => View): PartialFunction[Any, Seq[AnyRef]]

  def enableTileTrace(site: View, here: View, up: View): PartialFunction[Any, Any]
  def instantiateTile(param: TileParams, crossing: RocketCrossingParams,
    logicalTreeNode: LogicalTreeNode, p: Parameters): Option[BaseTile]
}

class CoreRegisterEntry[TileParamsT <: CoreParams, TileT <: BaseTile](
  tk: Field[Seq[TileParamsT]],
  ck: Field[Seq[RocketCrossingParams]],
  tileInstantiator: (TileParamsT, RocketCrossingParams, LookupByHartIdImpl, LogicalTreeNode, Parameters) => TileT
) extends CoreRegisterEntryBase {
  type TileParams = TileParamsT
  def tilesKey = tk
  def crossingKey = ck

  def findTilesWithFilter(view: View, p: Any => View) = {
    case key if (key == tk && p(tk)) => view(tk)
  }

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

// Core Generic Config - change properties in the given map
class GenericConfig(properties: Map[String, Any], filterFunc: Any => Bool = (_ => true)) {
  val configFunc: (View, View, View) => PartialFunction[Any, Any] = ((site, here, up) => key => {
    val tiles = CoreRegistrar.cores flatMap _.findTilesWithFilter(up, filterFunc).lift(key)
    if (tiles.size == 0) None else Some(tiles map (tile => {
      val method = ClassTag(tile.getClass).member(TermName(methodName)).asMethod
    })).unlift
  }).unlift
}
