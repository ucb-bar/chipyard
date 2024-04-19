package mdf.macrolib

import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

// TODO: decide if we should always silently absorb errors

// See macro_format.yml for the format description.

// "Base class" for macros
abstract class Macro {
  def name: String

  // Type of macro is determined by subclass
  def typeStr: String

  def toJSON(): JsObject
}
