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

// Extractor object accompanied class
// This is used to check the convertibility for those wrapped in Option, since Option's type is erased at runtime.
trait SubParameterBase {
  def toProduct: Product
  def cast(p: Any): Any
}
final class SubParameter[T <: Product](param: T) extends SubParameterBase {
  def toProduct: Product = param
  def cast(p: Any) = p.asInstanceOf[T]
}

// Extractor object that help identify the parameter case classes.
// Add your customized nested parameter classes (or their commom base classes) here.
object CustomizedSubParameter {
  def unapply(param: Product): Option[Product] = param match {
    // ADD YOUR NESTED PARAMETER CLASS HERE, in the format shown below in SubParameter
    case _ => None
  }
}

// Standard nested 
object SubParameter {
  def unapply(param: Product): Option[SubParameterBase] = param match {
    case p: TileParams => Some(new SubParameter(p))
    case p: CoreParams => Some(new SubParameter(p))
    case p: ICacheParams => Some(new SubParameter(p))
    case p: DCacheParams => Some(new SubParameter(p))
    case p: MulDivParams => Some(new SubParameter(p))
    case p: FPUParams => Some(new SubParameter(p))
    case p: BTBParams => Some(new SubParameter(p))
    case p: BHTParams => Some(new SubParameter(p))
    case CustomizedSubParameter(p) => Some(new SubParameter(p))
    case _ => None
  }
}

// Dynamic update helper for Parameter class. 
class CopyParam(paramExtracted: SubParameterBase) {
  // Constructor for corresponding TileParams
  private val param: Product = paramExtracted.toProduct
  private val paramClass = param.getClass
  private val paramNames = (paramClass.getDeclaredFields map (f => f.getName))
  private val paramCtor = paramClass.getConstructors.head

  // Function to build value entry
  private def buildEntry(value: Any): Any = value match {
    case Some(v) => Some(buildEntry(v))
    case SubParameter(p) => new CopyParam(p)
    case v => v
  }

  // Value of the case class
  private val entries = param.productIterator.toList map (v => buildEntry(v))

  // Update one value entry
  private def updateEntry(entry: Any, newValue: Any): Any = entry match {
    case Some(e) => newValue match {
      case Some(v) => Some(updateEntry(e, v))
      case None => None
    }
    case e: CopyParam => newValue match {
      case newValues: Map[String, Any] => e.update(newValues)
      case v => paramExtracted.cast(v)
    }
    // Use cast() to check the type of the new value. Here I assume that all entries in the parameters class are simple values
    // (like Int, BigInt and String), which are all final. This may breaks if a polymorphic type is added (unless it's a case
    // class and registered above). 
    case e => e.getClass.cast(newValue)
  }

  // Update the entire parameter object. 
  def update(newValues: Map[String, Any]): Any = {
    val filteredValues = newValues.filter({ case (key, value) => paramNames contains key })
    val newValueList = entries.zipWithIndex map {
      case (value, i) if newValues contains paramNames(i) => updateEntry(value, filteredValues(paramNames(i))).asInstanceOf[AnyRef]
      case (value, i) => (value match {
        case Some(v) => v match {
          case copyParam: CopyParam => Some(copyParam.param)
          case _ => Some(v)
        }
        case copyParam: CopyParam => copyParam.param
        case _ => value
      }).asInstanceOf[AnyRef]
    }
    paramCtor.newInstance(newValueList:_*)
  }

  // For debug purpose - print what's in the object
  override def toString(): String = paramClass.getSimpleName + "(" + entries.toString + ")"
}

object CopyParam {
  def apply(param: Product, newValues: Map[String, Any]): Any = param match {
    case SubParameter(p) => new CopyParam(p).update(newValues)
    case _ => throw new Exception("param is not a known Parameter type: add your custom parameter class to GenericCoreConfig.scala to fix it")
  }
}

// Change parameters for all registered cores in CoreManager.
class GenericCoreConfig (
  // Key-value pairs to be updated (keys are the name of fields). Any field not in a core's parameters will be ignored.
  // If a field is a case class containing parameters (or an Option of that), you can use another Map containing the key-value pairs to 
  // update that case class. Using a new case class instance as the value is also acceptable.
  // If a field is an Option, you should wrap your new values with Some() or set it to None. This also applies when a new case 
  // class instance is used for an Option field.
  newValues: Map[String, Any],
  // Function for filtering the list of TilesKey.
  filterFunc: Any => Boolean = (_ => true),
  // Handling special cases where partial function input is not a TilesKey.
  specialCase: (View, View, View) => PartialFunction[Any, Any] = ((_, _, _) => Map.empty)  
) extends Config((site, here, up) =>
  scala.Function.unlift((key: Any) => {
    val tiles = CoreManager.cores flatMap (core => core.updateWithFilter(up, filterFunc).lift(key))
    if (tiles.size == 0) None else Some(tiles(0)(newValues))
  }).orElse(specialCase(site, here, up))
)