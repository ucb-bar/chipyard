package testchipip.serdes.old

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config._
import freechips.rocketchip.util.HellaPeekingArbiter
import freechips.rocketchip.tilelink._

abstract class DecoupledSerialIO(w: Int) extends Bundle {
  val in = Flipped(Decoupled(UInt(w.W)))
  val out = Decoupled(UInt(w.W))
}

trait SerialParams {
  val width: Int
  val asyncQueueSz: Int
  def genIO: Bundle
}

// A decoupled flow-control serial interface where all signals are synchronous to
// a locally-produced clock
class InternalSyncSerialIO(w: Int) extends DecoupledSerialIO(w) {
  val clock_out = Output(Clock())
}
case class InternalSyncSerialParams(width: Int = 4, freqMHz: Int = 100, asyncQueueSz: Int = 8) extends SerialParams {
  def genIO = new InternalSyncSerialIO(width)
}

// A decoupled flow-control serial interface where all signals are synchronous to
// an externally produced clock
class ExternalSyncSerialIO(w: Int) extends DecoupledSerialIO(w) {
  val clock_in = Input(Clock())
}
case class ExternalSyncSerialParams(width: Int = 4, asyncQueueSz: Int = 8) extends SerialParams {
  def genIO = new ExternalSyncSerialIO(width)
}

// A credited flow-control serial interface where all signals are synchronous to
// a slock provided by the transmitter of that signal
class SourceSyncSerialIO(val w: Int) extends Bundle {
  val clock_in = Input(Clock())
  val reset_out = Output(AsyncReset())
  val clock_out = Output(Clock())
  val reset_in = Input(AsyncReset())
  val in = Input(Valid(UInt(w.W)))
  val credit_in = Input(Bool())
  val out = Output(Valid(UInt(w.W)))
  val credit_out = Output(Bool())
}
case class SourceSyncSerialParams(width: Int = 4, freqMHz: Int = 100, asyncQueueSz: Int = 16) extends SerialParams {
  def genIO = new SourceSyncSerialIO(width)
}

class SerialIO(val w: Int) extends Bundle {
  val in = Flipped(Decoupled(UInt(w.W)))
  val out = Decoupled(UInt(w.W))

  def flipConnect(other: SerialIO) {
    in <> other.out
    other.in <> out
  }
}

class ValidSerialIO(val w: Int) extends Bundle {
  val in = Flipped(Valid(UInt(w.W)))
  val out = Valid(UInt(w.W))

  def flipConnect(other: ValidSerialIO) {
    in <> other.out
    other.in <> out
  }
}

class StreamChannel(val w: Int) extends Bundle {
  val data = UInt(w.W)
  val keep = UInt((w/8).W)
  val last = Bool()
}

class StreamIO(val w: Int) extends Bundle {
  val in = Flipped(Decoupled(new StreamChannel(w)))
  val out = Decoupled(new StreamChannel(w))

  def flipConnect(other: StreamIO) {
    in <> other.out
    other.in <> out
  }
}

class StreamNarrower(inW: Int, outW: Int) extends Module {
  require(inW > outW)
  require(inW % outW == 0)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new StreamChannel(inW)))
    val out = Decoupled(new StreamChannel(outW))
  })

  val outBytes = outW / 8
  val outBeats = inW / outW

  val bits = Reg(new StreamChannel(inW))
  val count = Reg(UInt(log2Ceil(outBeats).W))

  val s_recv :: s_send :: Nil = Enum(2)
  val state = RegInit(s_recv)

  val nextData = bits.data >> outW.U
  val nextKeep = bits.keep >> outBytes.U

  io.in.ready := state === s_recv
  io.out.valid := state === s_send
  io.out.bits.data := bits.data(outW - 1, 0)
  io.out.bits.keep := bits.keep(outBytes - 1, 0)
  io.out.bits.last := bits.last && !nextKeep.orR

  when (io.in.fire) {
    count := (outBeats - 1).U
    bits := io.in.bits
    state := s_send
  }

  when (io.out.fire) {
    count := count - 1.U
    bits.data := nextData
    bits.keep := nextKeep
    when (io.out.bits.last || count === 0.U) {
      state := s_recv
    }
  }
}

class StreamWidener(inW: Int, outW: Int) extends Module {
  require(outW > inW)
  require(outW % inW == 0)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new StreamChannel(inW)))
    val out = Decoupled(new StreamChannel(outW))
  })

  val inBytes = inW / 8
  val inBeats = outW / inW

  val data = Reg(Vec(inBeats, UInt(inW.W)))
  val keep = RegInit(VecInit(Seq.fill(inBeats)(0.U(inBytes.W))))
  val last = Reg(Bool())

  val idx = RegInit(0.U(log2Ceil(inBeats).W))

  val s_recv :: s_send :: Nil = Enum(2)
  val state = RegInit(s_recv)

  io.in.ready := state === s_recv
  io.out.valid := state === s_send
  io.out.bits.data := data.asUInt
  io.out.bits.keep := keep.asUInt
  io.out.bits.last := last

  when (io.in.fire) {
    idx := idx + 1.U
    data(idx) := io.in.bits.data
    keep(idx) := io.in.bits.keep
    when (io.in.bits.last || idx === (inBeats - 1).U) {
      last := io.in.bits.last
      state := s_send
    }
  }

  when (io.out.fire) {
    idx := 0.U
    keep.foreach(_ := 0.U)
    state := s_recv
  }
}

object StreamWidthAdapter {
  def apply(out: DecoupledIO[StreamChannel], in: DecoupledIO[StreamChannel]) {
    if (out.bits.w > in.bits.w) {
      val widener = Module(new StreamWidener(in.bits.w, out.bits.w))
      widener.io.in <> in
      out <> widener.io.out
    } else if (out.bits.w < in.bits.w) {
      val narrower = Module(new StreamNarrower(in.bits.w, out.bits.w))
      narrower.io.in <> in
      out <> narrower.io.out
    } else {
      out <> in
    }
  }

  def apply(a: StreamIO, b: StreamIO) {
    apply(a.out, b.out)
    apply(b.in, a.in)
  }
}

class ValidStreamIO(w: Int) extends Bundle {
  val in = Flipped(Valid(new StreamChannel(w)))
  val out = Valid(new StreamChannel(w))

  def flipConnect(other: ValidStreamIO) {
    in <> other.out
    other.in <> out
  }

}

class GenericSerializer[T <: Data](t: T, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(t))
    val out = Decoupled(UInt(w.W))
    val busy = Output(Bool())
  })

  val dataBits = t.getWidth
  val dataBeats = (dataBits - 1) / w + 1
  val data = Reg(UInt(dataBits.W))

  val sending = RegInit(false.B)
  val (sendCount, sendDone) = Counter(io.out.fire, dataBeats)

  io.in.ready := !sending
  io.out.valid := sending
  io.out.bits := data(w-1, 0)
  io.busy := sending

  when (io.in.fire) {
    data := io.in.bits.asUInt
    sending := true.B
  }

  when (io.out.fire) { data := data >> w.U }

  when (sendDone) { sending := false.B }
}

class GenericDeserializer[T <: Data](t: T, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(w.W)))
    val out = Decoupled(t)
    val busy = Output(Bool())
  })

  val dataBits = t.getWidth
  val dataBeats = (dataBits - 1) / w + 1
  val data = Reg(Vec(dataBeats, UInt(w.W)))

  val receiving = RegInit(true.B)
  val (recvCount, recvDone) = Counter(io.in.fire, dataBeats)

  io.in.ready := receiving
  io.out.valid := !receiving
  io.out.bits := data.asUInt.asTypeOf(t)
  io.busy := recvCount =/= 0.U || !receiving

  when (io.in.fire) {
    data(recvCount) := io.in.bits
  }

  when (recvDone) { receiving := false.B }

  when (io.out.fire) { receiving := true.B }
}

class SerdesDebugIO extends Bundle {
  val ser_busy = Bool()
  val des_busy = Bool()
}

