package sifive.blocks.devices.uart

object UARTCtrlRegs {
  val txfifo = 0x00
  val rxfifo = 0x04
  val txctrl = 0x08
  val txmark = 0x0a
  val rxctrl = 0x0c
  val rxmark = 0x0e

  val ie     = 0x10
  val ip     = 0x14
  val div    = 0x18
  val parity = 0x1c
  val wire4  = 0x20
  val either8or9 = 0x24
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
