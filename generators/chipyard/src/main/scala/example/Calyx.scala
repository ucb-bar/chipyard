package chipyard.example

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, PBUS}
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf


case class CalyxSumParams(
  address: BigInt = 0x5000,
  qDepth: Int = 4,
  nBits: Int = 4,
  nSum: Int = 3)

case object CalyxSumKey extends Field[Option[CalyxSumParams]](None)


class CalyxSumIO(nBits: Int) extends Bundle {
  val clk   = Input(Clock())
  val reset = Input(Bool())

  val in    = Input(UInt(nBits.W))
  val go    = Input(Bool())

  val out   = Output(UInt(nBits.W))
  val done  = Output(Bool())
}

class CalyxSumBlackBox(nBits: Int) extends BlackBox with HasBlackBoxResource {
  val io = IO(new CalyxSumIO(nBits))
  addResource("/vsrc/aggregator.sv")
}

class CalyxSumTopIO extends Bundle {
  val done = Output(Bool())
}

trait HasCalyxSumTopIO {
  def io: CalyxSumTopIO
}

class CalyxSumMMIOWrapper(
  params: CalyxSumParams, beatBytes: Int
)(
  implicit p: Parameters
) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("calyx-sum", Seq("ucbbar,calyx-sum")) 
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=beatBytes)

  val nBits = params.nBits
  val nSum  = params.nSum

  override lazy val module = new MMIOWrapperImpl

  class MMIOWrapperImpl extends Impl with HasCalyxSumTopIO {
    val io = IO(new CalyxSumTopIO)

    withClockAndReset(clock, reset) {
      val bb    = Module(new CalyxSumBlackBox(nBits))
      val in_q  = Module(new Queue(UInt(nBits.W), params.qDepth))
      val out_q = Module(new Queue(UInt(nBits.W), params.qDepth))

      val go = RegInit(false.B)
      val cnt = RegInit(0.U(8.W))

      switch (go) {
        is (false.B) {
          when (in_q.io.count > 0.U && out_q.io.enq.ready) {
            go := true.B
            cnt := 0.U
          }
        }

        is (true.B) {
          when (bb.io.done) {
            go := false.B
          }
        }
      }

      bb.io.clk   := clock
      bb.io.reset := reset.asBool
      bb.io.go    := go
      bb.io.in    := in_q.io.deq.bits
      in_q.io.deq.ready := bb.io.done

      out_q.io.enq.bits  := bb.io.out
      out_q.io.enq.valid := bb.io.done
      io.done := bb.io.done

      when (bb.io.done) {
        assert(out_q.io.enq.ready)
      }

      node.regmap(
        0x00 -> Seq(RegField.r(1,     in_q.io.enq.ready)),
        0x04 -> Seq(RegField.w(nBits, in_q.io.enq)),
        0x08 -> Seq(RegField.r(1,     out_q.io.deq.valid)),
        0x0C -> Seq(RegField.r(nBits, out_q.io.deq))
      )
    }
  }
}

trait CanHaveMMIOCalyxSum { this: BaseSubsystem =>
  private val pbus = locateTLBusWrapper(PBUS)

  val calyx_sum_done = p(CalyxSumKey) match {
    case Some(params) => {
      val cs = LazyModule(new CalyxSumMMIOWrapper(params, pbus.beatBytes)(p))
      cs.clockNode := pbus.fixedClockNode
      pbus.coupleTo("calyx_sum_mmio_wrapper") {
        cs.node := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
      }

      // Add port to DigitalTop (just for fun)
      val calyx_sum_done = InModuleBody {
        val done = IO(Output(Bool())).suggestName("calyx_sum_done")
        done := cs.module.io.done
        done
      }
      Some(calyx_sum_done)
    }
    case None => None
  }
}

class WithCalyxSum extends Config((site, here, up) => {
  case CalyxSumKey => Some(CalyxSumParams())
})
