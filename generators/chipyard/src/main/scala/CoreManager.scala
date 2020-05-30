package chipyard

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import chisel3._

import freechips.rocketchip.config.{Parameters, Config, Field, View}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, RocketCrossingParams}
import freechips.rocketchip.diplomacy.{LazyModule, ClockCrossingType, ValName}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import ariane.{ArianeTile, ArianeTilesKey, ArianeCrossingKey, ArianeTileParams}
import chipsalliance.rocketchip.config.Parameters

// Base trait for all third-party core entries
sealed trait CoreEntryBase {
  def tileParamsLookup(implicit p: Parameters): Seq[TileParams]
  def updateWithFilter(view: View, p: Any => Boolean): PartialFunction[Any, Map[String, Any] => Any]
  def instantiateTile(crossingLookup: (Seq[RocketCrossingParams], Int) => Seq[RocketCrossingParams], logicalTreeNode: LogicalTreeNode)
    (implicit p: Parameters, valName: ValName): Seq[(TileParams, RocketCrossingParams, BaseTile)]
}

// Implementation of third-party core entries
class CoreEntry[TileParamsT <: TileParams with Product: TypeTag, TileT <: BaseTile : TypeTag](
  tk: Field[Seq[TileParamsT]],
  ck: Field[Seq[RocketCrossingParams]]
) extends CoreEntryBase {
  private val mirror = runtimeMirror(getClass.getClassLoader)
  private val paramClass = mirror.runtimeClass(typeOf[TileParamsT].typeSymbol.asClass)
  private val paramNames = (paramClass.getDeclaredFields map (f => f.getName)).zipWithIndex.toMap
  private val paramCtr = paramClass.getConstructors.head

  private val tileClass = mirror.runtimeClass(typeOf[TileT].typeSymbol.asClass)
  private val tileCtr = tileClass.getConstructors.head

  // Reflective version of copy()
  def copyTileParam(tileParam: TileParamsT, properties: Map[String, Any]) = {
    val values = tileParam.productIterator.toList
    val indexedProperties = properties map { case (key, value) => (paramNames(key), value) }
    val newValues = (0 until values.size) map
      (i => (if (indexedProperties contains i) indexedProperties(i) else values(i)).asInstanceOf[AnyRef])
    paramCtr.newInstance(newValues:_*)
  }

  def tileParamsLookup(implicit p: Parameters) = p(tk)

  def updateWithFilter(view: View, p: Any => Boolean): PartialFunction[Any, Map[String, Any] => Any] = {
    case key if (key == tk && p(tk)) => properties => view(tk) map
      (tile => copyTileParam(tile, properties))
  }

  def instantiateTile(crossingLookup: (Seq[RocketCrossingParams], Int) => Seq[RocketCrossingParams], logicalTreeNode: LogicalTreeNode)
    (implicit p: Parameters, valName: ValName) = {
    val tileParams = p(tk)
    val crossings = crossingLookup(p(ck), tileParams.size)
    (tileParams zip crossings) map {
      case (param, crossing) => (
        param,
        crossing,
        LazyModule(tileCtr.newInstance(param, crossing, PriorityMuxHartIdFromSeq(tileParams), logicalTreeNode, p.asInstanceOf[Parameters]).asInstanceOf[TileT])
      )
    }
  }
}

// Core Generic Config - change properties in the given map
class GenericConfig(properties: Map[String, Any], filterFunc: Any => Boolean) {
  val configFunc: (View, View, View) => PartialFunction[Any, Any] = (site, here, up) => scala.Function.unlift((key: Any) => {
    val tiles = CoreManager.cores flatMap (core => core.updateWithFilter(up, filterFunc).lift(key))
    if (tiles.size == 0) None else Some(tiles map (tile => tile(properties)))
  })
}

object GenericConfig {
  def apply(properties: Map[String, Any], filterFunc: Any => Boolean = (_ => true)) =
    new GenericConfig(properties, filterFunc).configFunc
}

object CoreManager {
  val cores: List[CoreEntryBase] = List(
    // ADD YOUR CORE DEFINITION HERE
    new CoreEntry[ArianeTileParams, ArianeTile](ArianeTilesKey, ArianeCrossingKey)
  )
}
