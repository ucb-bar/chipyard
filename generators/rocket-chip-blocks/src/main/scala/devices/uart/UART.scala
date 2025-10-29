package sifive.blocks.devices.uart

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

/** UART parameters
  *
  * @param address uart device TL base address
  * @param dataBits number of bits in data frame
  * @param stopBits number of stop bits
  * @param divisorBits width of baud rate divisor
  * @param oversample constructs the times of sampling for every data bit
  * @param nSamples number of reserved Rx sampling result for decide one data bit
  * @param nTxEntries number of entries in fifo between TL bus and Tx
  * @param nRxEntries number of entries in fifo between TL bus and Rx
  * @param includeFourWire additional CTS/RTS ports for flow control
  * @param includeParity parity support
  * @param includeIndependentParity Tx and Rx have opposite parity modes
  * @param initBaudRate initial baud rate
  *
  * @note baud rate divisor = clk frequency / baud rate. It means the number of clk period for one data bit.
  *       Calculated in [[UARTAttachParams.attachTo()]]
  *
  * @example To configure a 8N1 UART with features below:
  *          {{{
  *            8 entries of Tx and Rx fifo
  *            Baud rate = 115200
  *            Rx samples each data bit 16 times
  *            Uses 3 sample result for each data bit
  *          }}}
  *          Set the stopBits as below and keep the other parameter unchanged
  *          {{{
  *            stopBits = 1
  *          }}}
  *
  */
case class UARTParams(
  address: BigInt,
  dataBits: Int = 8,
  stopBits: Int = 2,
  divisorBits: Int = 16,
  oversample: Int = 4,
  nSamples: Int = 3,
  nTxEntries: Int = 8,
  nRxEntries: Int = 8,
  includeFourWire: Boolean = false,
  includeParity: Boolean = false,
  includeIndependentParity: Boolean = false, // Tx and Rx have opposite parity modes
  initBaudRate: BigInt = BigInt(115200),
) extends DeviceParams
{
  def oversampleFactor = 1 << oversample
  require(divisorBits > oversample)
  require(oversampleFactor > nSamples)
  require((dataBits == 8) || (dataBits == 9))
}

class UARTPortIO(val c: UARTParams) extends Bundle {
  val txd = Output(Bool())
  val rxd = Input(Bool())
  val cts_n = c.includeFourWire.option(Input(Bool()))
  val rts_n = c.includeFourWire.option(Output(Bool()))
}

class UARTInterrupts extends Bundle {
  val rxwm = Bool()
  val txwm = Bool()
}

//abstract class UART(busWidthBytes: Int, val c: UARTParams, divisorInit: Int = 0)
/** UART Module organizes Tx and Rx module with fifo and generates control signals for them according to CSRs and UART parameters.
  *
  * ==Component==
  *  - Tx
  *  - Tx fifo
  *  - Rx
  *  - Rx fifo
  *  - TL bus to soc
  *
  * ==IO==
  * [[UARTPortIO]]
  *
  * ==Datapass==
  * {{{
  * TL bus -> Tx fifo -> Tx
  * TL bus <- Rx fifo <- Rx
  * }}}
  *
  * @param divisorInit: number of clk period for one data bit
  */
class UART(busWidthBytes: Int, val c: UARTParams, divisorInit: Int = 0)
                   (implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "serial",
        compat = Seq("sifive,uart0"),
        base = c.address,
        beatBytes = busWidthBytes),
      new UARTPortIO(c))
    //with HasInterruptSources {
    with HasInterruptSources with HasTLControlRegMap {

  def nInterrupts = 1 + c.includeParity.toInt

  ResourceBinding {
    Resource(ResourceAnchors.aliases, "uart").bind(ResourceAlias(device.label))
  }

  require(divisorInit != 0, "UART divisor wasn't initialized during instantiation")
  require(divisorInit >> c.divisorBits == 0, s"UART divisor reg (width $c.divisorBits) not wide enough to hold $divisorInit")

  lazy val module = new LazyModuleImp(this) {

  val txm = Module(new UARTTx(c))
  val txq = Module(new Queue(UInt(c.dataBits.W), c.nTxEntries))

  val rxm = Module(new UARTRx(c))
  val rxq = Module(new Queue(UInt(c.dataBits.W), c.nRxEntries))

  val div = RegInit(divisorInit.U(c.divisorBits.W))

  private val stopCountBits = log2Up(c.stopBits)
  private val txCountBits = log2Floor(c.nTxEntries) + 1
  private val rxCountBits = log2Floor(c.nRxEntries) + 1

  val txen = RegInit(false.B)
  val rxen = RegInit(false.B)
  val enwire4 = RegInit(false.B)
  val invpol = RegInit(false.B)
  val enparity = RegInit(false.B)
  val parity = RegInit(false.B) // Odd parity - 1 , Even parity - 0
  val errorparity = RegInit(false.B)
  val errie = RegInit(false.B)
  val txwm = RegInit(0.U(txCountBits.W))
  val rxwm = RegInit(0.U(rxCountBits.W))
  val nstop = RegInit(0.U(stopCountBits.W))
  val data8or9 = RegInit(true.B)

  if (c.includeFourWire){
    txm.io.en := txen && (!port.cts_n.get || !enwire4)
    txm.io.cts_n.get := port.cts_n.get
  }
  else 
    txm.io.en := txen
  txm.io.in <> txq.io.deq
  txm.io.div := div
  txm.io.nstop := nstop
  port.txd := txm.io.out

  if (c.dataBits == 9) {
    txm.io.data8or9.get := data8or9
    rxm.io.data8or9.get := data8or9
  }

  rxm.io.en := rxen
  rxm.io.in := port.rxd
  rxq.io.enq.valid := rxm.io.out.valid
  rxq.io.enq.bits := rxm.io.out.bits
  rxm.io.div := div
  val tx_busy = (txm.io.tx_busy || txq.io.count.orR) && txen
  port.rts_n.foreach { r => r := Mux(enwire4, !(rxq.io.count < c.nRxEntries.U), tx_busy ^ invpol) }
  if (c.includeParity) {
    txm.io.enparity.get := enparity
    txm.io.parity.get := parity
    rxm.io.parity.get := parity ^ c.includeIndependentParity.B // independent parity on tx and rx
    rxm.io.enparity.get := enparity
    errorparity := rxm.io.errorparity.get || errorparity
    interrupts(1) := errorparity && errie
  }

  val ie = RegInit(0.U.asTypeOf(new UARTInterrupts()))
  val ip = Wire(new UARTInterrupts)

  ip.txwm := (txq.io.count < txwm)
  ip.rxwm := (rxq.io.count > rxwm)
  interrupts(0) := (ip.txwm && ie.txwm) || (ip.rxwm && ie.rxwm)

  val mapping = Seq(
    UARTCtrlRegs.txfifo -> RegFieldGroup("txdata",Some("Transmit data"),
                           NonBlockingEnqueue(txq.io.enq)),
    UARTCtrlRegs.rxfifo -> RegFieldGroup("rxdata",Some("Receive data"),
                           NonBlockingDequeue(rxq.io.deq)),

    UARTCtrlRegs.txctrl -> RegFieldGroup("txctrl",Some("Serial transmit control"),Seq(
      RegField(1, txen,
               RegFieldDesc("txen","Transmit enable", reset=Some(0))),
      RegField(stopCountBits, nstop,
               RegFieldDesc("nstop","Number of stop bits", reset=Some(0))))),
    UARTCtrlRegs.rxctrl -> Seq(RegField(1, rxen,
               RegFieldDesc("rxen","Receive enable", reset=Some(0)))),
    UARTCtrlRegs.txmark -> Seq(RegField(txCountBits, txwm,
               RegFieldDesc("txcnt","Transmit watermark level", reset=Some(0)))),
    UARTCtrlRegs.rxmark -> Seq(RegField(rxCountBits, rxwm,
               RegFieldDesc("rxcnt","Receive watermark level", reset=Some(0)))),

    UARTCtrlRegs.ie -> RegFieldGroup("ie",Some("Serial interrupt enable"),Seq(
      RegField(1, ie.txwm,
               RegFieldDesc("txwm_ie","Transmit watermark interrupt enable", reset=Some(0))),
      RegField(1, ie.rxwm,
               RegFieldDesc("rxwm_ie","Receive watermark interrupt enable", reset=Some(0))))),

    UARTCtrlRegs.ip -> RegFieldGroup("ip",Some("Serial interrupt pending"),Seq(
      RegField.r(1, ip.txwm,
                 RegFieldDesc("txwm_ip","Transmit watermark interrupt pending", volatile=true)),
      RegField.r(1, ip.rxwm,
                 RegFieldDesc("rxwm_ip","Receive watermark interrupt pending", volatile=true)))),

    UARTCtrlRegs.div -> Seq(
      RegField(c.divisorBits, div,
                 RegFieldDesc("div","Baud rate divisor",reset=Some(divisorInit))))
  )

  val optionalparity = if (c.includeParity) Seq(
    UARTCtrlRegs.parity -> RegFieldGroup("paritygenandcheck",Some("Odd/Even Parity Generation/Checking"),Seq(
      RegField(1, enparity,
               RegFieldDesc("enparity","Enable Parity Generation/Checking", reset=Some(0))),
      RegField(1, parity,
               RegFieldDesc("parity","Odd(1)/Even(0) Parity", reset=Some(0))),
      RegField(1, errorparity,
               RegFieldDesc("errorparity","Parity Status Sticky Bit", reset=Some(0))),
      RegField(1, errie,
               RegFieldDesc("errie","Interrupt on error in parity enable", reset=Some(0)))))) else Nil

  val optionalwire4 = if (c.includeFourWire) Seq(
    UARTCtrlRegs.wire4 -> RegFieldGroup("wire4",Some("Configure Clear-to-send / Request-to-send ports / RS-485"),Seq(
      RegField(1, enwire4,
               RegFieldDesc("enwire4","Enable CTS/RTS(1) or RS-485(0)", reset=Some(0))),
      RegField(1, invpol,
               RegFieldDesc("invpol","Invert polarity of RTS in RS-485 mode", reset=Some(0)))
    ))) else Nil

  val optional8or9 = if (c.dataBits == 9) Seq(
    UARTCtrlRegs.either8or9 -> RegFieldGroup("ConfigurableDataBits",Some("Configure number of data bits to be transmitted"),Seq(
      RegField(1, data8or9,
               RegFieldDesc("databits8or9","Data Bits to be 8(1) or 9(0)", reset=Some(1)))))) else Nil
  regmap(mapping ++ optionalparity ++ optionalwire4 ++ optional8or9:_*)
}
}
class TLUART(busWidthBytes: Int, params: UARTParams, divinit: Int)(implicit p: Parameters)
  extends UART(busWidthBytes, params, divinit) with HasTLControlRegMap

case class UARTLocated(loc: HierarchicalLocation) extends Field[Seq[UARTAttachParams]](Nil)

case class UARTAttachParams(
  device: UARTParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLUART = where {
    val name = s"uart_${UART.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val divinit = (tlbus.dtsFrequency.get / device.initBaudRate).toInt
    val uartClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None, name = Some("TLUART")))
    val uart = uartClockDomainWrapper { LazyModule(new TLUART(tlbus.beatBytes, device, divinit)) }
    uart.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus, Some("UART_Blocker")) := _ }
        blocker
      }

      uartClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(uart.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val uartClockGroup = ClockGroup()
          uartClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := uartClockGroup } .getOrElse { uartClockGroup }
      })

      (uart.controlXing(controlXType)
        := TLFragmenter(tlbus, Some("UART"))
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := uart.intXing(intXType)

    uart
  }
}

object UART {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[UARTPortIO], name: String)(implicit p: Parameters): ModuleValue[UARTPortIO] = {
    val uartNode = node.makeSink()
    InModuleBody { uartNode.makeIO()(ValName(name)) }
  }

  def tieoff(port: UARTPortIO) {
    port.rxd := 1.U
    if (port.c.includeFourWire) {
      port.cts_n.foreach { ct => ct := false.B } // active-low
    }
  }

  def loopback(port: UARTPortIO) {
    port.rxd := port.txd
    if (port.c.includeFourWire) {
      port.cts_n.get := port.rts_n.get
    }
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
