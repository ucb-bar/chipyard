package sifive.blocks.devices.uart

import org.chipsalliance.cde.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{BaseSubsystem, PeripheryBusKey}

case object PeripheryUARTKey extends Field[Seq[UARTParams]](Nil)

trait HasPeripheryUART { this: BaseSubsystem =>
  val uarts = p(PeripheryUARTKey).map { ps =>
    UARTAttachParams(ps).attachTo(this)
  }
  val uartNodes = uarts.map(_.ioNode.makeSink())
  val uart = InModuleBody { uartNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"uart_$i")) } }
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
