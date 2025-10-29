package sifive.blocks.devices.spi

import chisel3._ 

object SPIProtocol {
  val width = 2
  def Single = 0.U(width.W)
  def Dual   = 1.U(width.W)
  def Quad   = 2.U(width.W)

  def cases = Seq(Single, Dual, Quad)
  def decode(x: UInt): Seq[Bool] = cases.map(_ === x)
}

object SPIDirection {
  val width = 1
  def Rx = 0.U(width.W)
  def Tx = 1.U(width.W)
}

object SPIEndian {
  val width = 1
  def MSB = 0.U(width.W)
  def LSB = 1.U(width.W)
}

object SPICSMode {
  val width = 2
  def Auto = 0.U(width.W)
  def Hold = 2.U(width.W)
  def Off  = 3.U(width.W)
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
