package sifive.blocks.util

import chisel3._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.diplomacy._

trait HasDeviceDFTPorts[T <: Bundle] {
  
  def dftNode: BundleBridgeSource[T]

  def makeDFTPort(implicit p: Parameters): ModuleValue[T] = {
    val dftSink = dftNode.makeSink()
    InModuleBody { dontTouch(dftSink.bundle) }
  }
}

trait CanHaveDFT { 
  
  implicit val p: Parameters

  def devices: Seq[LazyModule]

  val dftNodes = devices.collect { case source: HasDeviceDFTPorts[Bundle] => source }
  dftNodes.foreach(_.makeDFTPort)
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
