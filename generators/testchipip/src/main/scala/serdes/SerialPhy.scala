package testchipip.serdes

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._

class DecoupledSerialPhy(channels: Int, phyParams: SerialPhyParams) extends RawModule {

    println(Console.RED + s"""

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

WARNING: YOU ARE USING A DEADLOCKING DECOUPLED
SERIAL PHY. THIS SHOULD ONLY BE USED IF YOU ARE
CERTAIN THIS LINK WILL NOT BE HEAVILY LOADED.

USE CreditedSourceSyncSerialPhyParams INSTEAD IF
DEADLOCK-FREEDOM IS NECESSARY.

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

""" + Console.RESET)

  val io = IO(new Bundle {
    val outer_clock = Input(Clock())
    val outer_reset = Input(Reset())
    val inner_clock = Input(Clock())
    val inner_reset = Input(Reset())
    val outer_ser = new DecoupledPhitIO(phyParams.phitWidth)
    val inner_ser = Flipped(Vec(channels, new DecoupledFlitIO(phyParams.flitWidth)))
  })


  val out_phits = (0 until channels).map { i =>
    val out_async = Module(new AsyncQueue(new Phit(phyParams.phitWidth)))
    out_async.io.enq_clock := io.inner_clock
    out_async.io.enq_reset := io.inner_reset.asBool
    out_async.io.deq_clock := io.outer_clock
    out_async.io.deq_reset := io.outer_reset.asBool
    out_async.io.enq <> withClockAndReset(io.inner_clock, io.inner_reset) {
      FlitToPhit(Queue(io.inner_ser(i).out, phyParams.flitBufferSz), phyParams.phitWidth)
    }
    out_async.io.deq
  }

  val out_arb = withClockAndReset(io.outer_clock, io.outer_reset) {
    Module(new PhitArbiter(phyParams.phitWidth, phyParams.flitWidth, channels))
  }
  out_arb.io.in <> out_phits
  io.outer_ser.out <> out_arb.io.out

  val in_phits = (0 until channels).map { i =>
    val in_async = Module(new AsyncQueue(new Phit(phyParams.phitWidth)))
    in_async.io.enq_clock := io.outer_clock
    in_async.io.enq_reset := io.outer_reset.asBool
    in_async.io.deq_clock := io.inner_clock
    in_async.io.deq_reset := io.inner_reset.asBool
    io.inner_ser(i).in <> withClockAndReset(io.inner_clock, io.inner_reset) {
      Queue(PhitToFlit(in_async.io.deq, phyParams.flitWidth), phyParams.flitBufferSz)
    }
    in_async.io.enq
  }
  val in_demux = withClockAndReset(io.outer_clock, io.outer_reset) {
    Module(new PhitDemux(phyParams.phitWidth, phyParams.flitWidth, channels))
  }

  in_demux.io.in <> io.outer_ser.in
  in_demux.io.out <> in_phits

  // Prevent accepting data from external world when in reset
  when (io.outer_reset.asBool) {
    io.outer_ser.in.ready  := false.B
    io.outer_ser.out.valid := false.B
  }
}

class CreditedSerialPhy(channels: Int, phyParams: SerialPhyParams) extends RawModule {
  val io = IO(new Bundle {
    val incoming_clock = Input(Clock())
    val incoming_reset = Input(Reset())
    val outgoing_clock = Input(Clock())
    val outgoing_reset = Input(Reset())
    val inner_clock = Input(Clock())
    val inner_reset = Input(Reset())

    val outer_ser = new ValidPhitIO(phyParams.phitWidth)
    val inner_ser = Flipped(Vec(channels, new DecoupledFlitIO(phyParams.flitWidth)))
  })

  val (out_data_phits, out_credit_phits) = (0 until channels).map { i =>
    val out_data_async = Module(new AsyncQueue(new Phit(phyParams.phitWidth)))
    out_data_async.io.enq_clock := io.inner_clock
    out_data_async.io.enq_reset := io.inner_reset.asBool
    out_data_async.io.deq_clock := io.outgoing_clock
    out_data_async.io.deq_reset := io.outgoing_reset.asBool
    val out_credit_async = Module(new AsyncQueue(new Phit(phyParams.phitWidth)))
    out_credit_async.io.enq_clock := io.incoming_clock
    out_credit_async.io.enq_reset := io.incoming_reset.asBool
    out_credit_async.io.deq_clock := io.inner_clock
    out_credit_async.io.deq_reset := io.inner_reset.asBool

    withClockAndReset(io.inner_clock, io.inner_reset) {
      val out_to_credited = Module(new DecoupledFlitToCreditedFlit(phyParams.flitWidth, phyParams.flitBufferSz))
      out_to_credited.io.in <> io.inner_ser(i).out
      out_data_async.io.enq <> FlitToPhit(out_to_credited.io.out, phyParams.phitWidth)
      out_to_credited.io.credit <> PhitToFlit(out_credit_async.io.deq, phyParams.flitWidth)
    }
    (out_data_async.io.deq, out_credit_async.io.enq)
  }.unzip

  val (in_data_phits, in_credit_phits) = (0 until channels).map { i =>
    val in_data_async = Module(new AsyncQueue(new Phit(phyParams.phitWidth)))
    in_data_async.io.enq_clock := io.incoming_clock
    in_data_async.io.enq_reset := io.incoming_reset.asBool
    in_data_async.io.deq_clock := io.inner_clock
    in_data_async.io.deq_reset := io.inner_reset.asBool
    val in_credit_async = Module(new AsyncQueue(new Phit(phyParams.phitWidth)))
    in_credit_async.io.enq_clock := io.incoming_clock
    in_credit_async.io.enq_reset := io.incoming_reset.asBool
    in_credit_async.io.deq_clock := io.outgoing_clock
    in_credit_async.io.deq_reset := io.outgoing_reset.asBool

    val in_data_phit = Wire(Decoupled(new Phit(phyParams.phitWidth)))
    withClockAndReset(io.incoming_clock, io.incoming_reset) {
      val credited_to_in = Module(new CreditedFlitToDecoupledFlit(phyParams.flitWidth, phyParams.flitBufferSz))
      credited_to_in.io.in <> PhitToFlit(in_data_phit, phyParams.flitWidth)
      in_data_async.io.enq <> FlitToPhit(credited_to_in.io.out, phyParams.phitWidth)
      in_credit_async.io.enq <> FlitToPhit(credited_to_in.io.credit, phyParams.phitWidth)
    }
    withClockAndReset(io.inner_clock, io.inner_reset) {
      io.inner_ser(i).in <> PhitToFlit(in_data_async.io.deq, phyParams.flitWidth)
    }

    (in_data_phit, in_credit_async.io.deq)
  }.unzip

  val out_arb = withClockAndReset(io.outgoing_clock, io.outgoing_reset) {
    Module(new PhitArbiter(phyParams.phitWidth, phyParams.flitWidth, channels * 2))
  }
  out_arb.io.in <> (out_data_phits ++ in_credit_phits)
  out_arb.io.out.ready := true.B
  io.outer_ser.out.valid := out_arb.io.out.valid
  io.outer_ser.out.bits := out_arb.io.out.bits

  val in_demux = withClockAndReset(io.incoming_clock, io.incoming_reset) {
    Module(new PhitDemux(phyParams.phitWidth, phyParams.flitWidth, channels * 2))
  }
  in_demux.io.in.valid := io.outer_ser.in.valid
  in_demux.io.in.bits := io.outer_ser.in.bits
  withClockAndReset(io.incoming_clock, io.incoming_reset) { when (io.outer_ser.in.valid) { assert(in_demux.io.in.ready) } }

  in_data_phits.zip(in_demux.io.out.take(channels)).map(t => t._1 <> t._2)
  out_credit_phits.zip(in_demux.io.out.drop(channels)).map(t => t._1 <> t._2)
}
