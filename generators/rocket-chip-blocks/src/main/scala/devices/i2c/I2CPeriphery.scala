package sifive.blocks.devices.i2c

import chisel3._
import org.chipsalliance.cde.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{BaseSubsystem}

case object PeripheryI2CKey extends Field[Seq[I2CParams]](Nil)

trait HasPeripheryI2C { this: BaseSubsystem =>
  val i2cNodes =  p(PeripheryI2CKey).map { ps =>
    I2CAttachParams(ps).attachTo(this).ioNode.makeSink()
  }
  val i2c  = InModuleBody { i2cNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"i2c_$i")) } }
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
