package sifive.blocks.devices.spi

import chisel3._
import chisel3.util._

abstract class SPIBundle(val c: SPIParamsBase) extends Bundle

class SPIDataIO extends Bundle {
  val i = Input(Bool())
  val o = Output(Bool())
  val ie = Output(Bool())
  val oe = Output(Bool())
}

class SPIPortIO(c: SPIParamsBase) extends SPIBundle(c) {
  val sck = Output(Bool())
  val dq = Vec(4, new SPIDataIO)
  val cs = Vec(c.csWidth, Output(Bool()))
}

trait HasSPIProtocol {
  val proto = Bits(SPIProtocol.width.W)
}
trait HasSPIEndian {
  val endian = Bits(SPIEndian.width.W)
}
class SPIFormat(c: SPIParamsBase) extends SPIBundle(c)
    with HasSPIProtocol
    with HasSPIEndian {
  val iodir = Bits(SPIDirection.width.W)
}

trait HasSPILength extends SPIBundle {
  val len = UInt(c.lengthBits.W)
}

class SPIClocking(c: SPIParamsBase) extends SPIBundle(c) {
  val div = UInt(c.divisorBits.W)
  val pol = Bool()
  val pha = Bool()
}

class SPIChipSelect(c: SPIParamsBase) extends SPIBundle(c) {
  val id = UInt(c.csIdBits.W)
  val dflt = Vec(c.csWidth, Bool())

  def toggle(en: Bool): Vec[Bool] = {
    val mask = en << id
    val out = Cat(dflt.reverse) ^ mask
    VecInit.tabulate(c.csWidth)(out(_))
  }
}

trait HasSPICSMode {
  val mode = Bits(SPICSMode.width.W)
}

class SPIDelay(c: SPIParamsBase) extends SPIBundle(c) {
  val cssck = UInt(c.delayBits.W)
  val sckcs = UInt(c.delayBits.W)
  val intercs = UInt(c.delayBits.W)
  val interxfr = UInt(c.delayBits.W)
}

class SPIWatermark(c: SPIParamsBase) extends SPIBundle(c) {
  val tx = UInt(c.txDepthBits.W)
  val rx = UInt(c.rxDepthBits.W)
}

class SPIControl(c: SPIParamsBase) extends SPIBundle(c) {
  val fmt = new SPIFormat(c) with HasSPILength
  val sck = new SPIClocking(c)
  val cs = new SPIChipSelect(c) with HasSPICSMode
  val dla = new SPIDelay(c)
  val wm = new SPIWatermark(c)
  val extradel = new SPIExtraDelay(c)
  val sampledel = new SPISampleDelay(c)
}

object SPIControl {
  def init(c: SPIParamsBase): SPIControl = {
    val ctrl = Wire(new SPIControl(c))
    ctrl.fmt.proto := SPIProtocol.Single
    ctrl.fmt.iodir := SPIDirection.Rx
    ctrl.fmt.endian := SPIEndian.MSB
    ctrl.fmt.len := (math.min(c.frameBits, 8)).U
    ctrl.sck.div := 3.U
    ctrl.sck.pol := false.B
    ctrl.sck.pha := false.B
    ctrl.cs.id := 0.U
    ctrl.cs.dflt.foreach { _ := true.B }
    ctrl.cs.mode := SPICSMode.Auto
    ctrl.dla.cssck := 1.U
    ctrl.dla.sckcs := 1.U
    ctrl.dla.intercs := 1.U
    ctrl.dla.interxfr := 0.U
    ctrl.wm.tx := 0.U
    ctrl.wm.rx := 0.U
    ctrl.extradel.coarse := 0.U
    ctrl.extradel.fine := 0.U
    ctrl.sampledel.sd := c.defaultSampleDel.U
    ctrl
  }
}

class SPIInterrupts extends Bundle {
  val txwm = Bool()
  val rxwm = Bool()
}

/*
   Copyright 2016 SiFive, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
