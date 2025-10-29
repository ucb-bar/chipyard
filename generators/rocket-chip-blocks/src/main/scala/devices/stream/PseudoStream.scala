package sifive.blocks.devices.stream

import chisel3._ 
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._

import sifive.blocks.util._

case class PseudoStreamParams(
    address: BigInt,
    nChannels: Int = 1,
    dataBits: Int = 32) extends DeviceParams {
  require(dataBits <= 63)
}

class PseudoStreamChannelIO(val params: PseudoStreamParams) extends Bundle {
  val txq = Decoupled(UInt(params.dataBits.W))
  val rxq = Flipped(Decoupled(UInt(params.dataBits.W)))
}

class PseudoStreamPortIO(val params: PseudoStreamParams) extends Bundle {
  val channel = Vec(params.nChannels, new PseudoStreamChannelIO(params))
}

abstract class PseudoStream(busWidthBytes: Int, val params: PseudoStreamParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "stream",
        compat = Seq("sifive,stream0"),
        base = params.address,
        size = 1 << log2Up(4096 * params.nChannels),
        beatBytes = busWidthBytes),
      new PseudoStreamPortIO(params)) {
  lazy val module = new LazyModuleImp(this) {

    val nbports = Wire(Vec(params.nChannels, new PseudoStreamChannelIO(params)))
    val bports = Wire(Vec(params.nChannels, new PseudoStreamChannelIO(params)))

    regmap(
      List.tabulate(params.nChannels)(idx => List(
        PseudoStreamCtrlRegs.txfifo + 4096 * idx -> RegFieldGroup(
          s"txfifo${idx}",
          Some(s"Non-Blocking Transmit FIFO for Channel ${idx}"),
          NonBlockingEnqueue(nbports(idx).txq, 64),
        ),
        PseudoStreamCtrlRegs.rxfifo + 4096 * idx -> RegFieldGroup(
          s"rxfifo${idx}",
          Some(s"Non-Blocking Receive FIFO for Channel ${idx}"),
          NonBlockingDequeue(nbports(idx).rxq, 64),
        ),
        PseudoStreamCtrlRegs.txfifob + 4096 * idx -> RegFieldGroup(
          s"txfifob${idx}",
          Some(s"Blocking Transmit FIFO for Channel ${idx}"),
          Seq(RegField.w(
            params.dataBits,
            bports(idx).txq,
            RegFieldDesc("data", s"Blocking Transmit data"),
          ))
        ),
        PseudoStreamCtrlRegs.rxfifob + 4096 * idx -> RegFieldGroup(
          s"rxfifob${idx}",
          Some(s"Blocking Receive FIFO for Channel ${idx}"),
          Seq(RegField.r(
            params.dataBits, bports(idx).rxq,
            RegFieldDesc("data", s"Blocking Receive data", volatile=true),
          )),
        )
      )).flatten: _*
    )

    (nbports zip bports).zipWithIndex.map { case ((nb, b), idx) =>
      val txq_arb = Module(new Arbiter(UInt(params.dataBits.W), 2))
    txq_arb.io.in(0) <> nb.txq
    txq_arb.io.in(1) <> b.txq
    port.channel(idx).txq <> txq_arb.io.out

    nb.rxq.valid := port.channel(idx).rxq.valid
    nb.rxq.bits := port.channel(idx).rxq.bits
    b.rxq.valid := port.channel(idx).rxq.valid
    b.rxq.bits := port.channel(idx).rxq.bits
    port.channel(idx).rxq.ready := nb.rxq.ready || b.rxq.ready
  }
}}

class TLPseudoStream(busWidthBytes: Int, params: PseudoStreamParams)(implicit p: Parameters)
  extends PseudoStream(busWidthBytes, params) with HasTLControlRegMap

case class PseudoStreamLocated(loc: HierarchicalLocation) extends Field[Seq[PseudoStreamAttachParams]](Nil)

case class PseudoStreamAttachParams(
  device: PseudoStreamParams,
  controlWhere: TLBusWrapperLocation = SBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLPseudoStream = where {
    val name = s"stream_${PseudoStream.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val streamClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val stream = streamClockDomainWrapper { LazyModule(new TLPseudoStream(tlbus.beatBytes, device)) }
    stream.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      streamClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(stream.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val streamClockGroup = ClockGroup()
          streamClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := streamClockGroup } .getOrElse { streamClockGroup }
      })

      (stream.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    stream
  }
}

object PseudoStream {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[PseudoStreamPortIO], name: String)(implicit p: Parameters): ModuleValue[PseudoStreamPortIO] = {
    val streamNode = node.makeSink()
    InModuleBody { streamNode.makeIO()(ValName(name)) }
  }

  def tieoff(port: PseudoStreamPortIO) {
    port.channel.foreach { s =>
      s.txq.ready := true.B
      s.rxq.valid := false.B
    }
  }

  def loopback(port: PseudoStreamPortIO, clock: Clock) {
    port.channel.foreach { s =>
      val q = Module(new Queue(s.txq.bits, 2))
      q.clock := clock
      q.io.enq <> s.txq
      s.rxq <> q.io.deq
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
