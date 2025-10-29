package sifive.blocks.devices.wdt

import chisel3._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._

import sifive.blocks.util._
import sifive.blocks.devices.mockaon.WatchdogTimer

case object PeripheryWDTKey extends Field[Seq[WDTParams]](Nil)

trait HasPeripheryWDT { this: BaseSubsystem =>
  val wdtNodes = p(PeripheryWDTKey).map { ps =>
    WDTAttachParams(ps).attachTo(this).ioNode.makeSink()
  }
}

trait HasPeripheryWDTBundle {
  val wdt: Seq[WDTPortIO]
}

trait HasPeripheryWDTModuleImp extends LazyRawModuleImp with HasPeripheryWDTBundle{
  val outer: HasPeripheryWDT
  val wdt = outer.wdtNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"wdt_$i"))}
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