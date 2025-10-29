package testchipip.serdes

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config._

class GenericSerializer[T <: Data](t: T, flitWidth: Int) extends Module {
  override def desiredName = s"GenericSerializer_${t.typeName}w${t.getWidth}_f${flitWidth}"
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(t))
    val out = Decoupled(new Flit(flitWidth))
    val busy = Output(Bool())
  })

  val dataBits = t.getWidth.max(flitWidth)
  val dataBeats = (dataBits - 1) / flitWidth + 1
  require(dataBeats >= 1)
  val data = Reg(Vec(dataBeats, UInt(flitWidth.W)))
  val beat = RegInit(0.U(log2Ceil(dataBeats).W))

  io.in.ready := io.out.ready && beat === 0.U
  io.out.valid := io.in.valid || beat =/= 0.U
  io.out.bits.flit := Mux(beat === 0.U, io.in.bits.asUInt, data(beat))

  when (io.out.fire) {
    beat := Mux(beat === (dataBeats-1).U, 0.U, beat + 1.U)
    when (beat === 0.U) {
      data := io.in.bits.asTypeOf(Vec(dataBeats, UInt(flitWidth.W)))
      data(0) := DontCare // unused, DCE this
    }
  }

  io.busy := io.out.valid
}

class GenericDeserializer[T <: Data](t: T, flitWidth: Int) extends Module {
  override def desiredName = s"GenericDeserializer_${t.typeName}w${t.getWidth}_f${flitWidth}"
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Flit(flitWidth)))
    val out = Decoupled(t)
    val busy = Output(Bool())
  })

  val dataBits = t.getWidth.max(flitWidth)
  val dataBeats = (dataBits - 1) / flitWidth + 1
  require(dataBeats >= 1)
  val data = Reg(Vec(dataBeats-1, UInt(flitWidth.W)))
  val beat = RegInit(0.U(log2Ceil(dataBeats).W))

  io.in.ready := io.out.ready || beat =/= (dataBeats-1).U
  io.out.valid := io.in.valid && beat === (dataBeats-1).U
  io.out.bits := (if (dataBeats == 1) {
    io.in.bits.flit.asTypeOf(t)
  } else {
    Cat(io.in.bits.flit, data.asUInt).asTypeOf(t)
  })

  when (io.in.fire) {
    beat := Mux(beat === (dataBeats-1).U, 0.U, beat + 1.U)
    if (dataBeats > 1) {
      when (beat =/= (dataBeats-1).U) {
        data(beat(log2Ceil(dataBeats-1)-1,0)) := io.in.bits.flit
      }
    }
  }

  io.busy := beat =/= 0.U
}

class FlitToPhit(flitWidth: Int, phitWidth: Int) extends Module {
  override def desiredName = s"FlitToPhit_f${flitWidth}_p${phitWidth}"
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Flit(flitWidth)))
    val out = Decoupled(new Phit(phitWidth))
  })
  require(flitWidth >= phitWidth)

  val dataBeats = (flitWidth - 1) / phitWidth + 1
  val data = Reg(Vec(dataBeats-1, UInt(phitWidth.W)))
  val beat = RegInit(0.U(log2Ceil(dataBeats).W))

  io.in.ready := io.out.ready && beat === 0.U
  io.out.valid := io.in.valid || beat =/= 0.U
  io.out.bits.phit := (if (dataBeats == 1) io.in.bits.flit else Mux(beat === 0.U, io.in.bits.flit, data(beat-1.U)))

  when (io.out.fire) {
    beat := Mux(beat === (dataBeats-1).U, 0.U, beat + 1.U)
    when (beat === 0.U) {
      data := io.in.bits.asTypeOf(Vec(dataBeats, UInt(phitWidth.W))).tail
    }
  }
}

object FlitToPhit {
  def apply(flit: DecoupledIO[Flit], phitWidth: Int): DecoupledIO[Phit] = {
    val flit2phit = Module(new FlitToPhit(flit.bits.flitWidth, phitWidth))
    flit2phit.io.in <> flit
    flit2phit.io.out
  }
}

class PhitToFlit(flitWidth: Int, phitWidth: Int) extends Module {
  override def desiredName = s"PhitToFlit_p${phitWidth}_f${flitWidth}"
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Phit(phitWidth)))
    val out = Decoupled(new Flit(flitWidth))
  })
  require(flitWidth >= phitWidth)

  val dataBeats = (flitWidth - 1) / phitWidth + 1
  val data = Reg(Vec(dataBeats-1, UInt(phitWidth.W)))
  val beat = RegInit(0.U(log2Ceil(dataBeats).W))

  io.in.ready := io.out.ready || beat =/= (dataBeats-1).U
  io.out.valid := io.in.valid && beat === (dataBeats-1).U
  io.out.bits.flit := (if (dataBeats == 1) io.in.bits.phit else Cat(io.in.bits.phit, data.asUInt))

  when (io.in.fire) {
    beat := Mux(beat === (dataBeats-1).U, 0.U, beat + 1.U)
    if (dataBeats > 1) {
      when (beat =/= (dataBeats-1).U) {
        data(beat) := io.in.bits.phit
      }
    }
  }
}

object PhitToFlit {
  def apply(phit: DecoupledIO[Phit], flitWidth: Int): DecoupledIO[Flit] = {
    val phit2flit = Module(new PhitToFlit(flitWidth, phit.bits.phitWidth))
    phit2flit.io.in <> phit
    phit2flit.io.out
  }
  def apply(phit: ValidIO[Phit], flitWidth: Int): ValidIO[Flit] = {
    val phit2flit = Module(new PhitToFlit(flitWidth, phit.bits.phitWidth))
    phit2flit.io.in.valid := phit.valid
    phit2flit.io.in.bits := phit.bits
    when (phit.valid) { assert(phit2flit.io.in.ready) }
    val out = Wire(Valid(new Flit(flitWidth)))
    out.valid := phit2flit.io.out.valid
    out.bits := phit2flit.io.out.bits
    phit2flit.io.out.ready := true.B
    out
  }
}

class PhitArbiter(phitWidth: Int, flitWidth: Int, channels: Int) extends Module {
  override def desiredName = s"PhitArbiter_p${phitWidth}_f${flitWidth}_n${channels}"
  val io = IO(new Bundle {
    val in = Flipped(Vec(channels, Decoupled(new Phit(phitWidth))))
    val out = Decoupled(new Phit(phitWidth))
  })
  if (channels == 1) {
    io.out <> io.in(0)
  } else {
    val headerWidth = log2Ceil(channels)
    val headerBeats = (headerWidth - 1) / phitWidth + 1
    val flitBeats = (flitWidth - 1) / phitWidth + 1
    val beats = headerBeats + flitBeats
    val beat = RegInit(0.U(log2Ceil(beats).W))
    val chosen_reg = Reg(UInt(headerWidth.W))
    val chosen_prio = PriorityEncoder(io.in.map(_.valid))
    val chosen = Mux(beat === 0.U, chosen_prio, chosen_reg)
    val header_idx = if (headerBeats == 1) 0.U else beat(log2Ceil(headerBeats)-1,0)

    io.out.valid := VecInit(io.in.map(_.valid))(chosen)
    io.out.bits.phit := Mux(beat < headerBeats.U,
      chosen.asTypeOf(Vec(headerBeats, UInt(phitWidth.W)))(header_idx),
      VecInit(io.in.map(_.bits.phit))(chosen))

    for (i <- 0 until channels) {
      io.in(i).ready := io.out.ready && beat >= headerBeats.U && chosen_reg === i.U
    }

    when (io.out.fire) {
      beat := Mux(beat === (beats-1).U, 0.U, beat + 1.U)
      when (beat === 0.U) { chosen_reg := chosen_prio }
    }
  }
}

class PhitDemux(phitWidth: Int, flitWidth: Int, channels: Int) extends Module {
  override def desiredName = s"PhitDemux_p${phitWidth}_f${flitWidth}_n${channels}"
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Phit(phitWidth)))
    val out = Vec(channels, Decoupled(new Phit(phitWidth)))
  })
  if (channels == 1) {
    io.out(0) <> io.in
  } else {
    val headerWidth = log2Ceil(channels)
    val headerBeats = (headerWidth - 1) / phitWidth + 1
    val flitBeats = (flitWidth - 1) / phitWidth + 1
    val beats = headerBeats + flitBeats
    val beat = RegInit(0.U(log2Ceil(beats).W))
    val channel_vec = Reg(Vec(headerBeats, UInt(phitWidth.W)))
    val channel = channel_vec.asUInt(log2Ceil(channels)-1,0)
    val header_idx = if (headerBeats == 1) 0.U else beat(log2Ceil(headerBeats)-1,0)

    io.in.ready := beat < headerBeats.U || VecInit(io.out.map(_.ready))(channel)
    for (c <- 0 until channels) {
      io.out(c).valid := io.in.valid && beat >= headerBeats.U && channel === c.U
      io.out(c).bits.phit := io.in.bits.phit
    }

    when (io.in.fire) {
      beat := Mux(beat === (beats-1).U, 0.U, beat + 1.U)
      when (beat < headerBeats.U) {
        channel_vec(header_idx) := io.in.bits.phit
      }
    }
  }
}

class DecoupledFlitToCreditedFlit(flitWidth: Int, bufferSz: Int) extends Module {
  override def desiredName = s"DecoupledFlitToCreditedFlit_f${flitWidth}_b${bufferSz}"

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Flit(flitWidth)))
    val out = Decoupled(new Flit(flitWidth))
    val credit = Flipped(Decoupled(new Flit(flitWidth)))
  })
  val creditWidth = log2Ceil(bufferSz)
  require(creditWidth <= flitWidth)
  val credits = RegInit(0.U((creditWidth+1).W))
  val credit_incr = io.out.fire
  val credit_decr = io.credit.fire
  when (credit_incr || credit_decr) {
    credits := credits + credit_incr - Mux(io.credit.valid, io.credit.bits.flit +& 1.U, 0.U)
  }

  io.out.valid := io.in.valid && credits < bufferSz.U
  io.out.bits.flit := io.in.bits.flit
  io.in.ready := io.out.ready && credits < bufferSz.U

  io.credit.ready := true.B
}

class CreditedFlitToDecoupledFlit(flitWidth: Int, bufferSz: Int) extends Module {
  override def desiredName = s"CreditedFlitToDecoupledFlit_f${flitWidth}_b${bufferSz}"
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Flit(flitWidth)))
    val out = Decoupled(new Flit(flitWidth))
    val credit = Decoupled(new Flit(flitWidth))
  })
  val creditWidth = log2Ceil(bufferSz)
  require(creditWidth <= flitWidth)
  val buffer = Module(new Queue(new Flit(flitWidth), bufferSz))
  val credits = RegInit(0.U((creditWidth+1).W))
  val credit_incr = buffer.io.deq.fire
  val credit_decr = io.credit.fire
  when (credit_incr || credit_decr) {
    credits := credit_incr + Mux(credit_decr, 0.U, credits)
  }

  buffer.io.enq.valid := io.in.valid
  buffer.io.enq.bits := io.in.bits
  io.in.ready := true.B
  when (io.in.valid) { assert(buffer.io.enq.ready) }

  io.out <> buffer.io.deq

  io.credit.valid := credits =/= 0.U
  io.credit.bits.flit := credits - 1.U
}
