package barstools.tapeout.transforms.pads

import firrtl._
import firrtl.ir._

abstract class PadOrientation extends FirrtlNode
case object Horizontal extends PadOrientation {
  def serialize: String = "horizontal"
}
case object Vertical extends PadOrientation {
  def serialize: String = "vertical"
}

abstract class PadType extends FirrtlNode
case object DigitalPad extends PadType {
  def serialize: String = "digital"
  def inName: String = "in"
  def outName: String = "out"
}
case object AnalogPad extends PadType {
  def serialize: String = "analog"
  def ioName: String = "io"
}
case object SupplyPad extends PadType {
  def serialize: String = "supply"
}
case object NoPad extends PadType {
  def serialize: String = "none"
}

case object InOut extends Direction {
  def serialize: String = "inout"
}
case object NoDirection extends Direction {
  def serialize: String = "none"
}

abstract class PadSide extends FirrtlNode {
  def orientation: PadOrientation
}
case object Left extends PadSide {
  def serialize: String = "left"
  def orientation: PadOrientation = Horizontal
}
case object Right extends PadSide {
  def serialize: String = "right"
  def orientation: PadOrientation = Horizontal
}
case object Top extends PadSide {
  def serialize: String = "top"
  def orientation: PadOrientation = Vertical
}
case object Bottom extends PadSide {
  def serialize: String = "bottom"
  def orientation: PadOrientation = Vertical
}