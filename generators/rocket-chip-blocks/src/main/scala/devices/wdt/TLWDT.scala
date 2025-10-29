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

case class WDTParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4) extends DeviceParams

class WDTPortIO(val c: WDTParams) extends Bundle {
  val corerst = Input(Bool())
  val rst = Output(Bool())
}

abstract class WDT(busWidthBytes: Int, val params: WDTParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "wdt",
        compat = Seq("sifive,wdt0"),
        base = params.address,
        size = params.size,
        beatBytes = busWidthBytes),
      new WDTPortIO(params))
    with HasInterruptSources {

  def nInterrupts: Int = 1

  lazy val module = new LazyModuleImp(this) {
    val wdt = Module(new WatchdogTimer())
    interrupts := wdt.io.ip
    port.rst := wdt.io.rst
    wdt.io.corerst := port.corerst
    //regmap((GenericTimer.timerRegMap(wdt, 0, params.regBytes)):_*)
    val mapping = (GenericTimer.timerRegMap(wdt, 0, params.regBytes))
    regmap(mapping:_*)
  }
}

class TLWDT(busWidthBytes: Int, params: WDTParams)(implicit p: Parameters)
  extends WDT(busWidthBytes, params) with HasTLControlRegMap

case class WDTLocated(loc: HierarchicalLocation) extends Field[Seq[WDTAttachParams]](Nil)

case class WDTAttachParams(
  device: WDTParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLWDT = where {
    val name = s"wdt_${WDT.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val wdtClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val wdt = wdtClockDomainWrapper { LazyModule(new TLWDT(tlbus.beatBytes, device)) }
    wdt.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      wdtClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(wdt.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val wdtClockGroup = ClockGroup()
          wdtClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := wdtClockGroup } .getOrElse { wdtClockGroup }
      })

      (wdt.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := wdt.intXing(intXType)

    wdt
  }
}

object WDT {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[WDTPortIO], name: String)(implicit p: Parameters): ModuleValue[WDTPortIO] = {
    val wdtNode = node.makeSink()
    InModuleBody { wdtNode.makeIO()(ValName(name)) }
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
