package sifive.blocks.devices.gpio

import chisel3._ 

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._




import sifive.blocks.devices.pinctrl.{PinCtrl, Pin, BasePin, EnhancedPin, EnhancedPinCtrl}
import sifive.blocks.util.{DeviceParams,DeviceAttachParams}

// This is sort of weird because
// the IOF end up at the RocketChipTop
// level, and we have to do the pinmux
// outside of RocketChipTop.
// It would be better if the IOF were here and
// we could do the pinmux inside.

class GPIOPortIO(val c: GPIOParams) extends Bundle {
  val pins = Vec(c.width, new EnhancedPin())
}

class IOFPortIO(val w: Int) extends Bundle {
  val iof_0 = Flipped(Vec(w, new IOFPin))
  val iof_1 = Flipped(Vec(w, new IOFPin))
}

case class GPIOParams(
  address: BigInt,
  width: Int,
  includeIOF: Boolean = false,
  dsWidth: Int = 1,
  hasPS: Boolean = false,
  hasPOE: Boolean = false) extends DeviceParams

/** The base GPIO peripheral functionality, which uses the regmap API to
  * abstract over the bus protocol to which it is being connected
  */
abstract class GPIO(busWidthBytes: Int, c: GPIOParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "gpio",
        compat = Seq("sifive,gpio0", "sifive,gpio1"),
        base = c.address,
        beatBytes = busWidthBytes),
      new GPIOPortIO(c))
    with HasInterruptSources {
  val iofNode = c.includeIOF.option(BundleBridgeSource(() => new IOFPortIO(c.width)))
  val iofPort = iofNode.map { node => InModuleBody { node.bundle } }

  def nInterrupts = c.width
  override def extraResources(resources: ResourceBindings) = Map(
    "gpio-controller"      -> Nil,
    "#gpio-cells"          -> Seq(ResourceInt(2)),
    "interrupt-controller" -> Nil,
    "#interrupt-cells"     -> Seq(ResourceInt(2)))

  lazy val module = new LazyModuleImp(this) {

  //--------------------------------------------------
  // CSR Declarations
  // -------------------------------------------------

  // SW Control only.
  val portReg = RegInit(0.U(c.width.W))

  val oeReg  = Module(new AsyncResetRegVec(c.width, 0))
  val pueReg = Module(new AsyncResetRegVec(c.width, 0))
  val dsReg  = RegInit(VecInit(Seq.fill(c.dsWidth)(0.U(c.width.W))))
  val ieReg  = Module(new AsyncResetRegVec(c.width, 0))
  val psReg  = RegInit(0.U(c.width.W))
  val poeReg = Module(new AsyncResetRegVec(c.width, 0))

  // Synchronize Input to get valueReg
  val inVal = WireDefault(0.U(c.width.W))
  inVal := port.pins.map(_.i.ival).asUInt
  val inSyncReg  = SynchronizerShiftReg(inVal, 3, Some("inSyncReg"))
  val valueReg   = RegNext(inSyncReg, 0.U(c.width.W))

  // Interrupt Configuration
  val highIeReg = RegInit(0.U(c.width.W))
  val lowIeReg  = RegInit(0.U(c.width.W))
  val riseIeReg = RegInit(0.U(c.width.W))
  val fallIeReg = RegInit(0.U(c.width.W))
  val highIpReg = RegInit(0.U(c.width.W))
  val lowIpReg  = RegInit(0.U(c.width.W))
  val riseIpReg = RegInit(0.U(c.width.W))
  val fallIpReg = RegInit(0.U(c.width.W))
  val passthruHighIeReg = RegInit(0.U(c.width.W))
  val passthruLowIeReg  = RegInit(0.U(c.width.W))

  // HW IO Function
  val iofEnReg  = Module(new AsyncResetRegVec(c.width, 0))
  val iofSelReg = RegInit(0.U(c.width.W))
  
  // Invert Output
  val xorReg    = RegInit(0.U(c.width.W))

  //--------------------------------------------------
  // CSR Access Logic (most of this section is boilerplate)
  // -------------------------------------------------

  val rise = ~valueReg & inSyncReg;
  val fall = valueReg & ~inSyncReg;

  val iofEnFields =  if (c.includeIOF) (Seq(RegField.rwReg(c.width, iofEnReg.io,
                        Some(RegFieldDesc("iof_en","HW I/O functon enable", reset=Some(0))))))
                     else (Seq(RegField(c.width)))
  val iofSelFields = if (c.includeIOF) (Seq(RegField(c.width, iofSelReg,
                        RegFieldDesc("iof_sel","HW I/O function select", reset=Some(0)))))
                     else (Seq(RegField(c.width)))
  val psFields = if (c.hasPS) Seq(RegField(c.width, psReg,
                                  RegFieldDesc("ps","Weak PU/PD Resistor Selection", reset=Some(0))))
                 else Seq(RegField(c.width))
  val poeFields = if (c.hasPOE) Seq(RegField.rwReg(c.width, poeReg.io,
                                  Some(RegFieldDesc("poe"," Nandtree enable", reset=Some(0)))))
                  else (Seq(RegField(c.width)))
  val dsRegsAndDescs = Seq.tabulate(c.dsWidth)( i =>
                        Seq(RegField(c.width, dsReg(i),
                              RegFieldDesc(s"ds$i", s"Pin drive strength $i selection", reset=Some(0)))))
  val dsRegMap = for ((rd, i) <- dsRegsAndDescs.zipWithIndex)
                   yield (GPIOCtrlRegs.drive + 4*i -> Seq(RegField(c.width, dsReg(i),
                          RegFieldDesc(s"ds$i",s"Pin drive strength $i selection", reset=Some(0)))))

  // shift other register offset when c.dsWidth > 1
  val dsOffset = (c.dsWidth - 1) * 4

  // Note that these are out of order.
  val mapping = Seq(
    GPIOCtrlRegs.value     -> Seq(RegField.r(c.width, valueReg,
                                  RegFieldDesc("input_value","Pin value", volatile=true))),
    GPIOCtrlRegs.input_en  -> Seq(RegField.rwReg(c.width, ieReg.io,
                                  Some(RegFieldDesc("input_en","Pin input enable", reset=Some(0))))),
    GPIOCtrlRegs.output_en -> Seq(RegField.rwReg(c.width, oeReg.io,
                                  Some(RegFieldDesc("output_en","Pin output enable", reset=Some(0))))),
    GPIOCtrlRegs.port      -> Seq(RegField(c.width, portReg,
                                  RegFieldDesc("output_value","Output value", reset=Some(0)))),
    GPIOCtrlRegs.pullup_en -> Seq(RegField.rwReg(c.width, pueReg.io,
                                  Some(RegFieldDesc("pue","Internal pull-up enable", reset=Some(0))))),
    GPIOCtrlRegs.rise_ie + dsOffset -> Seq(RegField(c.width, riseIeReg,
                                  RegFieldDesc("rise_ie","Rise interrupt enable", reset=Some(0)))),
    GPIOCtrlRegs.rise_ip + dsOffset -> Seq(RegField.w1ToClear(c.width, riseIpReg, rise,
                                  Some(RegFieldDesc("rise_ip","Rise interrupt pending", volatile=true)))),
    GPIOCtrlRegs.fall_ie + dsOffset -> Seq(RegField(c.width, fallIeReg,
                                  RegFieldDesc("fall_ie", "Fall interrupt enable", reset=Some(0)))),
    GPIOCtrlRegs.fall_ip + dsOffset -> Seq(RegField.w1ToClear(c.width, fallIpReg, fall,
                                  Some(RegFieldDesc("fall_ip","Fall interrupt pending", volatile=true)))),
    GPIOCtrlRegs.high_ie + dsOffset -> Seq(RegField(c.width, highIeReg,
                                  RegFieldDesc("high_ie","High interrupt enable", reset=Some(0)))),
    GPIOCtrlRegs.high_ip + dsOffset -> Seq(RegField.w1ToClear(c.width, highIpReg, valueReg,
                                  Some(RegFieldDesc("high_ip","High interrupt pending", volatile=true)))),
    GPIOCtrlRegs.low_ie  + dsOffset -> Seq(RegField(c.width, lowIeReg,
                                  RegFieldDesc("low_ie","Low interrupt enable", reset=Some(0)))),
    GPIOCtrlRegs.low_ip  + dsOffset -> Seq(RegField.w1ToClear(c.width,lowIpReg, ~valueReg,
                                  Some(RegFieldDesc("low_ip","Low interrupt pending", volatile=true)))),
    GPIOCtrlRegs.iof_en  + dsOffset -> iofEnFields,
    GPIOCtrlRegs.iof_sel + dsOffset -> iofSelFields,
    GPIOCtrlRegs.out_xor + dsOffset -> Seq(RegField(c.width, xorReg,
                                  RegFieldDesc("out_xor","Output XOR (invert) enable", reset=Some(0)))),
    GPIOCtrlRegs.passthru_high_ie + dsOffset -> Seq(RegField(c.width, passthruHighIeReg,
                                         RegFieldDesc("passthru_high_ie", "Pass-through active-high interrupt enable", reset=Some(0)))),
    GPIOCtrlRegs.passthru_low_ie  + dsOffset -> Seq(RegField(c.width, passthruLowIeReg,
                                         RegFieldDesc("passthru_low_ie", "Pass-through active-low interrupt enable", reset=Some(0)))),
    GPIOCtrlRegs.ps  + dsOffset -> psFields,
    GPIOCtrlRegs.poe + dsOffset -> poeFields
  )
  regmap(mapping ++ dsRegMap :_*)

  //--------------------------------------------------
  // Actual Pinmux
  // -------------------------------------------------

  val swPinCtrl = Wire(Vec(c.width, new EnhancedPinCtrl()))

  // This strips off the valid.
  val iof0Ctrl = Wire(Vec(c.width, new IOFCtrl()))
  val iof1Ctrl = Wire(Vec(c.width, new IOFCtrl()))

  val iofCtrl = Wire(Vec(c.width, new IOFCtrl()))
  val iofPlusSwPinCtrl = Wire(Vec(c.width, new EnhancedPinCtrl()))

  for (pin <- 0 until c.width) {

    // Software Pin Control
    swPinCtrl(pin).pue    := pueReg.io.q(pin)
    swPinCtrl(pin).oval   := portReg(pin)
    swPinCtrl(pin).oe     := oeReg.io.q(pin)
    swPinCtrl(pin).ds     := dsReg(0)(pin)
    swPinCtrl(pin).ie     := ieReg.io.q(pin)
    swPinCtrl(pin).ds1    := dsReg(if (c.dsWidth > 1) 1 else 0)(pin)
    swPinCtrl(pin).ps     := psReg(pin)
    swPinCtrl(pin).poe    := poeReg.io.q(pin)

    val pre_xor = Wire(new EnhancedPinCtrl())

    if (c.includeIOF) {
      // Allow SW Override for invalid inputs.
      iof0Ctrl(pin)      <> swPinCtrl(pin)
      when (iofPort.get.iof_0(pin).o.valid) {
        iof0Ctrl(pin)    <> iofPort.get.iof_0(pin).o
      }

      iof1Ctrl(pin)      <> swPinCtrl(pin)
      when (iofPort.get.iof_1(pin).o.valid) {
        iof1Ctrl(pin)    <> iofPort.get.iof_1(pin).o
      }

      // Select IOF 0 vs. IOF 1.
      iofCtrl(pin)       <> Mux(iofSelReg(pin), iof1Ctrl(pin), iof0Ctrl(pin))

      // Allow SW Override for things IOF doesn't control.
      iofPlusSwPinCtrl(pin) <> swPinCtrl(pin)
      iofPlusSwPinCtrl(pin) <> iofCtrl(pin)
   
      // Final XOR & Pin Control
      pre_xor  := Mux(iofEnReg.io.q(pin), iofPlusSwPinCtrl(pin), swPinCtrl(pin))
    } else {
      pre_xor := swPinCtrl(pin)
    }

    port.pins(pin).o      := pre_xor
    port.pins(pin).o.oval := pre_xor.oval ^ xorReg(pin)

    // Generate Interrupts
    interrupts(pin) := (riseIpReg(pin) & riseIeReg(pin)) |
                         (fallIpReg(pin) & fallIeReg(pin)) |
                         (highIpReg(pin) & highIeReg(pin)) |
                         (lowIpReg(pin) & lowIeReg(pin)) |
                         (valueReg(pin) & passthruHighIeReg(pin)) |
                         (~valueReg(pin) & passthruLowIeReg(pin))

    if (c.includeIOF) {
      // Send Value to all consumers
      iofPort.get.iof_0(pin).i.ival := inSyncReg(pin)
      iofPort.get.iof_1(pin).i.ival := inSyncReg(pin)
    }
  }

  // Tie off undriven pins/wires.

  if (!c.includeIOF) {
    iofEnReg.io.d  := 0.U
    iofEnReg.io.en := false.B
    Seq(iof0Ctrl, iof1Ctrl, iofCtrl).foreach { ctrls =>
      ctrls.foreach { ctrl =>
        ctrl.oval  := false.B
        ctrl.oe    := false.B
        ctrl.ie    := false.B
        ctrl.valid := false.B
      }
    }
    iofPlusSwPinCtrl.foreach { ctrl =>
      ctrl.oval := false.B
      ctrl.oe   := false.B
      ctrl.ie   := false.B
      ctrl.pue  := false.B
      ctrl.ds   := false.B
      ctrl.ps   := false.B
      ctrl.ds1  := false.B
      ctrl.poe  := false.B
    }
  }

  if (!c.hasPOE) {
    poeReg.io.d  := 0.U
    poeReg.io.en := false.B
  }}
}

class TLGPIO(busWidthBytes: Int, params: GPIOParams)(implicit p: Parameters)
  extends GPIO(busWidthBytes, params) with HasTLControlRegMap

case class GPIOLocated(loc: HierarchicalLocation) extends Field[Seq[GPIOAttachParams]](Nil)

case class GPIOAttachParams(
  device: GPIOParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLGPIO = where {
    val name = s"gpio_${GPIO.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val gpioClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val gpio = gpioClockDomainWrapper { LazyModule(new TLGPIO(cbus.beatBytes, device)) }
    gpio.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, cbus.beatBytes, cbus.beatBytes)))
        cbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(cbus) := _ }
        blocker
      }

      gpioClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          cbus.dtsClk.map(_.bind(gpio.device))
          cbus.fixedClockNode
        case _: RationalCrossing =>
          cbus.clockNode
        case _: AsynchronousCrossing =>
          val gpioClockGroup = ClockGroup()
          gpioClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := gpioClockGroup } .getOrElse { gpioClockGroup }
      })

      (gpio.controlXing(controlXType)
        := TLFragmenter(cbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := gpio.intXing(intXType)

    gpio
  }
}

object GPIO {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[GPIOPortIO], name: String)(implicit p: Parameters): ModuleValue[GPIOPortIO] = {
    val gpioNode = node.makeSink()
    InModuleBody { gpioNode.makeIO()(ValName(name)) }
  }

  def tieoff(g: GPIOPortIO){
    g.pins.foreach { p =>
      p.i.ival := false.B
    }
  }

  def tieoff(f: IOFPortIO) {
    f.iof_0.foreach { iof => iof.default() }
    f.iof_1.foreach { iof => iof.default() }
  }

  def loopback(g: GPIOPortIO)(pinA: Int, pinB: Int) {
    require(g.pins.length > pinA, s"Pin ${pinA} out of range for GPIO port with only ${g.pins.length} pins")
    require(g.pins.length > pinB, s"Pin ${pinB} out of range for GPIO port with only ${g.pins.length} pins")
    g.pins.foreach {p =>
      p.i.ival := Mux(p.o.oe, p.o.oval, p.o.pue) & p.o.ie
    }
    val a = g.pins(pinA)
    val b = g.pins(pinB)
    // This logic is not QUITE right, it doesn't handle all the subtle cases.
    // It is probably simpler to just hook a pad up here and use attach()
    // to model this properly.
    a.i.ival := Mux(b.o.oe, (b.o.oval | b.o.pue), (a.o.pue | (a.o.oe & a.o.oval))) & a.o.ie
    b.i.ival := Mux(a.o.oe, (a.o.oval | b.o.pue), (b.o.pue | (b.o.oe & b.o.oval))) & b.o.ie
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
