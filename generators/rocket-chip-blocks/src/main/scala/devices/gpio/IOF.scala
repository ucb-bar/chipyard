package sifive.blocks.devices.gpio

import chisel3._
import chisel3.util._
import sifive.blocks.devices.pinctrl.{PinCtrl, Pin, BasePin, EnhancedPin, EnhancedPinCtrl}

// This is the actual IOF interface.pa
// Add a valid bit to indicate whether
// there is something actually connected
// to this.
class IOFCtrl extends PinCtrl {
  val valid = Bool()
}

// By default,
object IOFCtrl {
  def apply(): IOFCtrl = {
    val iof = Wire(new IOFCtrl())
    iof.valid := false.B
    iof.oval  := false.B
    iof.oe    := false.B
    iof.ie    := false.B
    iof
  }
}

// Package up the inputs and outputs
// for the IOF
class IOFPin extends Pin {
  val o  = Output(IOFCtrl())

  def default(): Unit = {
    this.o.oval  := false.B
    this.o.oe    := false.B
    this.o.ie    := false.B
    this.o.valid := false.B
  }

  def inputPin(pue: Bool = false.B /*ignored*/): Bool = {
    this.o.oval := false.B
    this.o.oe   := false.B
    this.o.ie   := true.B
    this.i.ival
  }
  def outputPin(signal: Bool,
    pue: Bool = false.B, /*ignored*/
    ds: Bool = false.B, /*ignored*/
    ie: Bool = false.B
  ): Unit = {
    this.o.oval := signal
    this.o.oe   := true.B
    this.o.ie   := ie
  }
}

// Connect both the i and o side of the pin,
// and drive the valid signal for the IOF.
object BasePinToIOF {
  def apply(pin: BasePin, iof: IOFPin): Unit = {
    iof <> pin
    iof.o.valid := true.B
  }
}

object InputPortToIOF {
  def apply(iof: IOFPin): Bool = {
    val pin = Wire(new BasePin())
    BasePinToIOF(pin, iof)
    pin.inputPin()
  }
}

object OutputPortToIOF {
  def apply(port: Bool, iof: IOFPin): Unit = {
    val pin = Wire(new BasePin())
    pin.outputPin(port)
    BasePinToIOF(pin, iof)
  }

  def apply(port: UInt, iofs: Vec[IOFPin], offset: Int) {
    require(offset >= 0, s"offset in OutputPortToIOF must be >= 0, not ${offset}")
    require((offset + port.getWidth) <= iofs.size, s"offset (${offset}) + port width(${port.getWidth}) must be <= IOF size (${iofs.size})")
    for (i <- 0 until port.getWidth) {
      apply(port(i), iofs(offset + i))
    }
  }
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
