package sifive.blocks.devices.spi

import chisel3._ 
import org.chipsalliance.cde.config.Field
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.diplomacy._

case object PeripherySPIKey extends Field[Seq[SPIParams]](Nil)

trait HasPeripherySPI { this: BaseSubsystem =>
  val tlSpiNodes = p(PeripherySPIKey).map { ps =>
    SPIAttachParams(ps).attachTo(this)
  }
  val spiNodes = tlSpiNodes.map { n => n.ioNode.makeSink() }
  val spi = InModuleBody { spiNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"spi_$i")) } }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]](Nil)

trait HasPeripherySPIFlash { this: BaseSubsystem =>
  val tlQSpiNodes = p(PeripherySPIFlashKey).map { ps =>
    SPIFlashAttachParams(ps, fBufferDepth = 8).attachTo(this)
  }
  val qspiNodes = tlQSpiNodes.map { n => n.ioNode.makeSink() }
  val qspi = InModuleBody { qspiNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"qspi_$i")) } }
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
