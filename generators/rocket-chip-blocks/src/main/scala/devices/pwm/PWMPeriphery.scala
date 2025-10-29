package sifive.blocks.devices.pwm

import chisel3._
import org.chipsalliance.cde.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryPWMKey extends Field[Seq[PWMParams]](Nil)

trait HasPeripheryPWM { this: BaseSubsystem =>
  val pwmNodes = p(PeripheryPWMKey).map { ps =>
    PWMAttachParams(ps).attachTo(this).ioNode.makeSink 
  }
  val pwm  = InModuleBody { pwmNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"pwm_$i")) } }
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
