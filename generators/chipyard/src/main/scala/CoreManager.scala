package chipyard

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import chisel3._

import freechips.rocketchip.config.{Parameters, Config, Field, View}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, RocketCrossingParams, RocketCrossingKey}
import freechips.rocketchip.diplomacy.{LazyModule, ClockCrossingType, ValName}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import boom.common.{BoomTile, BoomTilesKey, BoomCrossingKey, BoomTileParams}
import ariane.{ArianeTile, ArianeTilesKey, ArianeCrossingKey, ArianeTileParams}

// Base trait for all third-party core entries
sealed trait CoreEntryBase {
  val name: String
  def tileParamsLookup(implicit p: Parameters): Seq[TileParams]
  def updateWithFilter(view: View, p: Any => Boolean): PartialFunction[Any, Map[String, Any] => Any]
  def instantiateTile(crossingLookup: (Seq[RocketCrossingParams], Int) => Seq[RocketCrossingParams], logicalTreeNode: LogicalTreeNode)
    (implicit p: Parameters, valName: ValName): Seq[(TileParams, RocketCrossingParams, BaseTile)]
}

// Implementation of third-party core entries
class CoreEntry[TileParamsT <: TileParams with Product: TypeTag, TileT <: BaseTile : TypeTag](
  val name: String,
  tilesKey: Field[Seq[TileParamsT]],
  crossingKey: Field[Seq[RocketCrossingParams]]
) extends CoreEntryBase {
  // Use reflection to get the tile's constructor
  private val mirror = runtimeMirror(getClass.getClassLoader)
  private val tileClass = mirror.runtimeClass(typeOf[TileT].typeSymbol.asClass)
  private val tileCtor = tileClass.getConstructors.filter(ctor => ctor.getParameterTypes()(4) == classOf[Parameters]).head

  // Tile parameter lookup using correct type
  def tileParamsLookup(implicit p: Parameters) = p(tilesKey)

  // If this core meet the requirement given by p, update parameter fields in the map
  def updateWithFilter(view: View, p: Any => Boolean): PartialFunction[Any, Map[String, Any] => Any] = {
    case key if (key == tilesKey && p(tilesKey)) => newValues => view(tilesKey) map
      (tile => CopyParam(tile, newValues))
  }

  // Instantiate a tile and zip it with its parameter info, used by subsystem
  def instantiateTile(crossingLookup: (Seq[RocketCrossingParams], Int) => Seq[RocketCrossingParams], logicalTreeNode: LogicalTreeNode)
    (implicit p: Parameters, valName: ValName) = {
    val tileParams = p(tilesKey)
    val crossings = crossingLookup(p(crossingKey), tileParams.size)
    (tileParams zip crossings) map {
      case (param, crossing) => (
        param,
        crossing,
        LazyModule(tileCtor.newInstance(param, crossing, PriorityMuxHartIdFromSeq(tileParams), logicalTreeNode, p.asInstanceOf[Parameters]).asInstanceOf[TileT])
      )
    }
  }
}

// A list of all cores. 
object CoreManager {
  val cores: List[CoreEntryBase] = List(
    // TODO ADD YOUR CORE DEFINITION HERE; note that the 
    new CoreEntry[RocketTileParams, RocketTile]("Rocket", RocketTilesKey, RocketCrossingKey),
    new CoreEntry[BoomTileParams, BoomTile]("Boom", BoomTilesKey, BoomCrossingKey),
    new CoreEntry[ArianeTileParams, ArianeTile]("Ariane", ArianeTilesKey, ArianeCrossingKey)
  )
}
