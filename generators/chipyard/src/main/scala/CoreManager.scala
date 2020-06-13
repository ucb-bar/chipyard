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

case object CoreEntryKey extends Field[Seq[CoreEntryBase]](Nil)

// If this key is encountered by a GenericTilesKey extractor, throw immediately
// Inside the body of GenericTileConfig, suppressed will be set to true to prevent the extractor from throwing
case class GenericTilesKeyChecker(suppressed: Boolean) extends Field[Int](0)
case class GenericTilesKeyImp(key: Field[Seq[TileParams]]) extends Field[Seq[GenericTileParams]](Nil)
object GenericTilesKey {
  def apply(key: Field[Seq[TileParams]]) = GenericTilesKeyImp(key)
  def unapply(key: Any): Option[Field[Seq[TileParams]]] = key match {
    case GenericTilesKeyChecker(suppressed) if !suppressed => throw new Exception("GenericTilesKey must be in GenericTilesConfig")
    case GenericTilesKeyImp(key) => Some(key)
    case _ => None
  }
}

// Base trait for all third-party core entries
sealed trait CoreEntryBase {
  val name: String

  def keyEqual(key: Any): Boolean
  def tileParamsLookup(implicit p: Parameters): Seq[TileParams]

  def instantiateTile(crossingLookup: (Seq[RocketCrossingParams], Int) => Seq[RocketCrossingParams], logicalTreeNode: LogicalTreeNode)
    (implicit p: Parameters, valName: ValName): Seq[(TileParams, RocketCrossingParams, () => BaseTile)]
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

  def keyEqual(key: Any) = key == tilesKey

  // Tile parameter lookup using correct type
  def tileParamsLookup(implicit p: Parameters) = p(tilesKey)

  // Instantiate a tile and zip it with its parameter info, used by subsystem
  def instantiateTile(crossingLookup: (Seq[RocketCrossingParams], Int) => Seq[RocketCrossingParams], logicalTreeNode: LogicalTreeNode)
    (implicit p: Parameters, valName: ValName) = {
    // Sanity check of GenericTilesKey outside of GenericTileConfig
    // People would shoot themselves in the foot easily with this design, so a sanity check is necessary
    // Simply trigger the exception by looking up the checker key
    p(GenericTilesKeyChecker(false))

    val tileParams = p(tilesKey)
    val crossings = crossingLookup(p(crossingKey), tileParams.size)
    (tileParams zip crossings) map {
      case (param, crossing) => (
        param,
        crossing,
        (() => LazyModule(tileCtor.newInstance(
          param,
          crossing,
          PriorityMuxHartIdFromSeq(tileParams),
          logicalTreeNode,
          p.asInstanceOf[Parameters]
        ).asInstanceOf[TileT]))
      )
    }
  }
}

// Config fragment to register a core
class RegisterCore[TileParamsT <: TileParams with Product: TypeTag, TileT <: BaseTile : TypeTag](
  name: String,
  tilesKey: Field[Seq[TileParamsT]],
  crossingKey: Field[Seq[RocketCrossingParams]]
) extends Config((site, here, up) => {
  case CoreEntryKey => new CoreEntry[TileParamsT, TileT](name, tilesKey, crossingKey) +: up(CoreEntryKey)
})

// The config used along with GenericTilesKey. 
// It change a lookup for registered tile parameter into a lookup with GenericTilesKey in the function body temporarily.
class GenericTileConfig(f: (View, View, View) => PartialFunction[Any, Any]) extends Config(
  new Config((site, here, up) => {
    case GenericTilesKeyChecker(_) => up(GenericTilesKeyChecker(true))
    case key if CoreManager.keyMatch(up, key) => up(GenericTilesKey(key.asInstanceOf[Field[Seq[TileParams]]])) map (t => t.convert)
  }) ++
  new Config(f) ++
  new Config((site, here, up) => {
    case GenericTilesKeyChecker(_) => up(GenericTilesKeyChecker(false))
    case GenericTilesKey(key) => up(key) map (t => new GenericTileParams(t))
  })
)

// A list of all cores. 
object CoreManager {
  // Built-in cores.
  val base_cores: List[CoreEntryBase] = List(
    new CoreEntry[RocketTileParams, RocketTile]("Rocket", RocketTilesKey, RocketCrossingKey),
    new CoreEntry[BoomTileParams, BoomTile]("Boom", BoomTilesKey, BoomCrossingKey),
    new CoreEntry[ArianeTileParams, ArianeTile]("Ariane", ArianeTilesKey, ArianeCrossingKey)
  )

  // Look up all cores that are registered in the current config view.
  def cores(view: View): Seq[CoreEntryBase] = view(CoreEntryKey) ++ base_cores

  // Check if the key is among the currently registered cores.
  def keyMatch(view: View, key: Any) = (cores(view) filter (c => c.keyEqual(key))).size != 0
}
