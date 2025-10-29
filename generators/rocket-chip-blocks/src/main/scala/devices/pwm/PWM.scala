package sifive.blocks.devices.pwm

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._




import sifive.blocks.util._

// Core PWM Functionality  & Register Interface
class PWMTimer(val ncmp: Int = 4, val cmpWidth: Int = 16, val prefix: String = "pwm") extends Module with GenericTimer {

  def orR(v: Vec[Bool]): Bool = v.foldLeft(false.B)( _||_ )
  protected def countWidth = ((1 << scaleWidth) - 1) + cmpWidth
  protected lazy val countAlways = RegEnable(io.regs.cfg.write.countAlways, false.B, io.regs.cfg.write_countAlways && unlocked)
  protected lazy val feed = count.carryOut(scale + cmpWidth.U)
  protected lazy val countEn = Wire(Bool())
  override protected lazy val oneShot = RegEnable(io.regs.cfg.write.running && !countReset, false.B, (io.regs.cfg.write_running && unlocked) || countReset)
  override protected lazy val extra: Vec[Bool]  = RegEnable(io.regs.cfg.write.extra, VecInit.fill(maxcmp){false.B}, orR(io.regs.cfg.write_extra) && unlocked)
  override protected lazy val center: Vec[Bool] = RegEnable(io.regs.cfg.write.center, orR(io.regs.cfg.write_center) && unlocked)
  override protected lazy val gang: Vec[Bool] = RegEnable(io.regs.cfg.write.gang, orR(io.regs.cfg.write_gang) && unlocked)
  override protected lazy val deglitch = RegEnable(io.regs.cfg.write.deglitch, io.regs.cfg.write_deglitch && unlocked)(0)
  override protected lazy val sticky = RegEnable(io.regs.cfg.write.sticky, io.regs.cfg.write_sticky && unlocked)(0)
  override protected lazy val ip = {
    val doSticky = RegNext((deglitch && !countReset) || sticky)
    val sel = (0 until ncmp).map(i => s(cmpWidth-1) && center(i))
    val reg = Reg(Vec(ncmp, Bool()))
    reg := (sel & elapsed) | (~sel & (elapsed | (VecInit.fill(ncmp){doSticky} & reg)))
    when (orR(io.regs.cfg.write_ip) && unlocked) { reg := io.regs.cfg.write.ip.take(ncmp) }
    reg
  }

  override protected lazy val feed_desc = RegFieldDesc.reserved
  override protected lazy val key_desc = RegFieldDesc.reserved
  override protected lazy val cfg_desc = DefaultGenericTimerCfgDescs(prefix, ncmp).copy(
    extra = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}invert${i}", s"Invert Comparator ${i} Output", reset = Some(0))}
  )

  class PWMTimerIO extends GenericTimerIO(regWidth, ncmp, maxcmp, scaleWidth, countWidth, cmpWidth) {
    val gpio = Output(Vec(ncmp, Bool()))
  }

  lazy val io = IO(new PWMTimerIO)

  val invert = extra.asUInt

  val ipU = ip.asUInt
  val gangU = gang.asUInt

  io.gpio := ((ipU & ~(gangU & Cat(ipU(0), ipU >> 1))) ^ invert).asTypeOf(io.gpio)
  countEn := countAlways || oneShot
}

case class PWMParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4,
  ncmp: Int = 4,
  cmpWidth: Int = 16) extends DeviceParams

class PWMPortIO(val c: PWMParams) extends Bundle {
  val gpio = Output(Vec(c.ncmp, Bool()))
}

abstract class PWM(busWidthBytes: Int, val params: PWMParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "pwm",
        compat = Seq("sifive,pwm0"),
        base = params.address,
        size = params.size,
        beatBytes = busWidthBytes),
      new PWMPortIO(params))
    with HasInterruptSources {

  def nInterrupts = params.ncmp
  override def extraResources(resources: ResourceBindings) = Map[String, Seq[ResourceValue]](
    "sifive,comparator-widthbits" -> Seq(ResourceInt(params.cmpWidth)),
    "sifive,ncomparators" -> Seq(ResourceInt(params.ncmp))
    )

  lazy val module = new LazyModuleImp(this) {
    val pwm = Module(new PWMTimer(params.ncmp, params.cmpWidth, "pwm"))
    interrupts := pwm.io.ip
    port.gpio := pwm.io.gpio
    //regmap((GenericTimer.timerRegMap(pwm, 0, params.regBytes)):_*)
    val mapping = (GenericTimer.timerRegMap(pwm, 0, params.regBytes))
    regmap(mapping:_*)
  }
}

class TLPWM(busWidthBytes: Int, params: PWMParams)(implicit p: Parameters)
  extends PWM(busWidthBytes, params) with HasTLControlRegMap

case class PWMLocated(loc: HierarchicalLocation) extends Field[Seq[PWMAttachParams]](Nil)

case class PWMAttachParams(
  device: PWMParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLPWM = {
    val name = s"pwm_${PWM.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val pwmClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val pwm = pwmClockDomainWrapper { LazyModule(new TLPWM(tlbus.beatBytes, device)) }
    pwm.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      pwmClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(pwm.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val pwmClockGroup = ClockGroup()
          pwmClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := pwmClockGroup } .getOrElse { pwmClockGroup }
      })

      (pwm.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := pwm.intXing(intXType)

    pwm
  }
}

object PWM {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[PWMPortIO], name: String)(implicit p: Parameters): ModuleValue[PWMPortIO] = {
    val pwmNode = node.makeSink()
    InModuleBody { pwmNode.makeIO()(ValName(name)) }
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
