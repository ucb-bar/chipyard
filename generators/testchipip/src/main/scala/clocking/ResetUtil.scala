package testchipip.clocking

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.util._
import freechips.rocketchip.prci.{ClockGroupAdapterNode}
import freechips.rocketchip.diplomacy._


class ResetStretcher(cycles: Int) extends Module {
  override def desiredName = s"ResetStretcher$cycles"
  val io = IO(new Bundle {
    val reset_out = Output(Bool())
  })
  val n = log2Ceil(cycles)
  val count = Module(new AsyncResetRegVec(w=n, init=0))
  val resetout = Module(new AsyncResetRegVec(w=1, init=1))
  count.io.en := resetout.io.q
  count.io.d := count.io.q + 1.U
  resetout.io.en := resetout.io.q
  resetout.io.d := count.io.q < (cycles-1).U
  io.reset_out := resetout.io.q.asBool
}

object ResetStretcher {
  def apply(clock: Clock, reset: Reset, cycles: Int): Reset = {
    withClockAndReset(clock, reset) {
      val stretcher = Module(new ResetStretcher(cycles))
      stretcher.io.reset_out
    }
  }
}

/**
  * Instantiates a FAKE reset synchronizer on all clock-reset pairs in a clock group.
  */
class ClockGroupFakeResetSynchronizer(implicit p: Parameters) extends LazyModule {
  val node = ClockGroupAdapterNode()
  lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
    println(Console.RED + s"""

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

WARNING: YOU ARE CASTING ASYNC RESET TO SYNC RESET
ACROSS ALL CLOCK DOMAINS. THIS WILL BREAK PHYSICAL
IMPLEMENTATIONS.

THIS SHOULD ONLY BE USED IN RTL SIMULATORS WHICH
HAVE TROUBLE HANDLING ASYNC RESET

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

""" + Console.RESET)
    (node.out zip node.in).map { case ((oG, _), (iG, _)) =>
      (oG.member.data zip iG.member.data).foreach { case (o, i) =>
        o.clock := i.clock
        o.reset := i.reset.asBool
      }
    }
  }
}

object ClockGroupFakeResetSynchronizer {
  def apply()(implicit p: Parameters, valName: ValName) = LazyModule(new ClockGroupFakeResetSynchronizer()).node
}

class ResetSync(c: Clock, lat: Int = 2) extends Module {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val reset_sync = Output(Bool())
  })
  clock := c
  io.reset_sync := ShiftRegister(io.reset,lat)
}

object ResetSync {
  def apply(r: Bool, c: Clock): Bool = {
    val sync = Module(new ResetSync(c,2))
    sync.suggestName("resetSyncInst")
    sync.io.reset := r
    sync.io.reset_sync
  }
}

