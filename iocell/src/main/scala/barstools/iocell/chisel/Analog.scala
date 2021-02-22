// See LICENSE for license details

package barstools.iocell.chisel

import chisel3._
import chisel3.util.{HasBlackBoxResource}
import chisel3.experimental.{Analog, IntParam}

class AnalogConst(value: Int, width: Int = 1)
    extends BlackBox(Map("CONST" -> IntParam(value), "WIDTH" -> IntParam(width)))
    with HasBlackBoxResource {
  val io = IO(new Bundle { val io = Analog(width.W) })
  addResource("/barstools/iocell/vsrc/Analog.v")
}

object AnalogConst {
  def apply(value: Int, width: Int = 1) = Module(new AnalogConst(value, width)).io.io
}
