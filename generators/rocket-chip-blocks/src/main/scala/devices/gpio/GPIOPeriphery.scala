package sifive.blocks.devices.gpio

import org.chipsalliance.cde.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]](Nil)

trait HasPeripheryGPIO { this: BaseSubsystem =>
  val (gpioNodes, iofNodes) = p(PeripheryGPIOKey).map { ps =>
    val gpio = GPIOAttachParams(ps).attachTo(this)
    (gpio.ioNode.makeSink(), gpio.iofNode.map { _.makeSink() })
  }.unzip

  val gpio = InModuleBody { gpioNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"gpio_$i")) } }
  val iof = InModuleBody { iofNodes.zipWithIndex.map { case(o,i) => o.map { n => n.makeIO()(ValName(s"iof_$i")) } } }
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
