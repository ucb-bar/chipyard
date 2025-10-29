
package sifive.blocks.devices.pinctrl

import chisel3._
import chisel3.util._

// This is the base class of things you "always"
// want to control from a HW block.
class PinCtrl extends Bundle {
  val oval = Bool()
  val oe   = Bool()
  val ie   = Bool()
}

// Package up the inputs and outputs
// for the Pin
abstract class Pin extends Bundle {
  val i = new Bundle {
    val ival = Input(Bool())
    val po   = Option(Input(Bool()))
  }
  val o: PinCtrl

  // Must be defined by the subclasses
  def default(): Unit
  def inputPin(pue: Bool = false.B): Bool
  def outputPin(signal: Bool,
    pue: Bool = false.B,
    ds: Bool = false.B,
    ie: Bool = false.B
  ): Unit
  
}


////////////////////////////////////////////////////////////////////////////////////

class BasePin extends Pin() {
  val o = Output(new PinCtrl())

  def default(): Unit = {
    this.o.oval := false.B
    this.o.oe   := false.B
    this.o.ie   := false.B
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

/////////////////////////////////////////////////////////////////////////
class EnhancedPinCtrl extends PinCtrl {
  val pue = Bool()
  val ds  = Bool()
  val ps  = Bool()
  val ds1 = Bool()
  val poe = Bool()

}

class EnhancedPin  extends Pin() {

  val o = Output(new EnhancedPinCtrl())

  def default(): Unit = {
    this.o.oval := false.B
    this.o.oe   := false.B
    this.o.ie   := false.B
    this.o.ds   := false.B
    this.o.pue  := false.B
    this.o.ds1  := false.B
    this.o.ps   := false.B
    this.o.poe  := false.B

  }

  def inputPin(pue: Bool = false.B): Bool = {
    this.o.oval := false.B
    this.o.oe   := false.B
    this.o.pue  := pue
    this.o.ds   := false.B
    this.o.ie   := true.B
    this.o.ds1  := false.B
    this.o.ps   := false.B
    this.o.poe  := false.B

    this.i.ival
  }

  def outputPin(signal: Bool,
    pue: Bool = false.B,
    ds: Bool = false.B,
    ie: Bool = false.B
  ): Unit = {
    this.o.oval := signal
    this.o.oe   := true.B
    this.o.pue  := pue
    this.o.ds   := ds
    this.o.ie   := ie
  }

  def toBasePin(): BasePin = {

    val base_pin = Wire(new BasePin())
    base_pin <> this
    base_pin
  }

  def nandInput(poe: Bool = true.B) : Bool = {
    this.i.po.get
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
