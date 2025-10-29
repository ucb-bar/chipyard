package sifive.blocks.devices.spi

import chisel3._ 

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.util._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._




import sifive.blocks.util._

case class SPILocated(loc: HierarchicalLocation) extends Field[Seq[SPIAttachParams]](Nil)

case class SPIAttachParams(
  device: SPIParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLSPI = where {
    val name = s"spi_${SPI.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val spiClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val spi = spiClockDomainWrapper { LazyModule(new TLSPI(tlbus.beatBytes, device)) }
    spi.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      spiClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(spi.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val spiClockGroup = ClockGroup()
          spiClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := spiClockGroup } .getOrElse { spiClockGroup }
      })

      (spi.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := spi.intXing(intXType)

    spi
  }
}

case class SPIFlashLocated(loc: HierarchicalLocation) extends Field[Seq[SPIFlashAttachParams]](Nil)

case class SPIFlashAttachParams(
  device: SPIFlashParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  dataWhere: TLBusWrapperLocation = PBUS,
  fBufferDepth: Int = 0,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing,
  memXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLSPIFlash = where {
    val name = s"qspi_${SPI.nextFlashId()}" // TODO should these be shared with regular SPIs?
    val cbus = where.locateTLBusWrapper(controlWhere)
    val mbus = where.locateTLBusWrapper(dataWhere)
    val qspiClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val qspi = qspiClockDomainWrapper { LazyModule(new TLSPIFlash(cbus.beatBytes, device)) }
    qspi.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, cbus.beatBytes, cbus.beatBytes)))
        cbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(cbus) := _ }
        blocker
      }

      qspiClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          cbus.dtsClk.map(_.bind(qspi.device))
          cbus.fixedClockNode
        case _: RationalCrossing =>
          cbus.clockNode
        case _: AsynchronousCrossing =>
          val qspiClockGroup = ClockGroup()
          qspiClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := qspiClockGroup } .getOrElse { qspiClockGroup }
      })

      (qspi.controlXing(controlXType)
        := TLFragmenter(cbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    mbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a+0x1000, mbus.beatBytes, mbus.beatBytes)))
        mbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(mbus) := _ }
        blocker
      }

      (qspi.memXing(memXType)
        := TLFragmenter(1, mbus.blockBytes)
        := TLBuffer(BufferParams(fBufferDepth), BufferParams.none)
        := TLWidthWidget(mbus.beatBytes)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := qspi.intXing(intXType)

    qspi
  }
}

object SPI {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[SPIPortIO], name: String)(implicit p: Parameters): ModuleValue[SPIPortIO] = {
    val spiNode = node.makeSink()
    InModuleBody { spiNode.makeIO()(ValName(name)) }
  }

  val nextFlashId = { var i = -1; () => { i += 1; i} }

  def makeFlashPort(node: BundleBridgeSource[SPIPortIO], name: String)(implicit p: Parameters): ModuleValue[SPIPortIO] = {
    val qspiNode = node.makeSink()
    InModuleBody { qspiNode.makeIO()(ValName(name)) }
  }

  def connectPort(q: SPIPortIO): SPIPortIO = {
    val x = Wire(new SPIPortIO(q.c))
    x <> q
    x
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
