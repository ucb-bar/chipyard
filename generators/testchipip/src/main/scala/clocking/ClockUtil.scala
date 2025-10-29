package testchipip.clocking

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, IntParam}

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util._

// This always adds the resource always
class EICG_wrapper extends ClockGate {
   addResource("/vsrc/EICG_wrapper.v")
}

class ClockFlop extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
        val clockIn = Input(Clock())
        val d = Input(Bool())
        val clockOut = Output(Clock())
    })

    addResource("/testchipip/vsrc/ClockUtil.v")
}

class ClockOr2 extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
        val clocksIn = Input(Vec(2, Clock()))
        val clockOut = Output(Clock())
    })

    addResource("/testchipip/vsrc/ClockUtil.v")
}

class ClockInverter extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
        val clockIn = Input(Clock())
        val clockOut = Output(Clock())
    })

    addResource("/testchipip/vsrc/ClockUtil.v")
}

object ClockInverter {
    def apply(in: Clock): Clock = {
        val x = Module(new ClockInverter)
        x.io.clockIn := in
        x.io.clockOut
    }
}

class ClockSignalNor2 extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
        val clockIn = Input(Clock())
        val signalIn = Input(Bool())
        val clockOut = Output(Clock())
    })

    addResource("/testchipip/vsrc/ClockUtil.v")
}

object ClockSignalNor2 {
    def apply(in: Clock, sig: Bool): Clock = {
        val x = Module(new ClockSignalNor2)
        x.io.clockIn := in
        x.io.signalIn := sig
        x.io.clockOut
    }
}

// XXX A clock multiplexer that does NOT safely transition between clocks
// Be very careful using this!
class ClockMux2 extends BlackBox with HasBlackBoxResource {

    val io = IO(new Bundle {
        val clocksIn = Input(Vec(2, Clock()))
        val sel = Input(Bool())
        val clockOut = Output(Clock())
    })

    addResource("/testchipip/vsrc/ClockUtil.v")
}

// A clock mux that's safe to switch during execution
// n: Number of inputs
// depth: Synchronizer depth
class ClockMutexMux(val n: Int, depth: Int, genClockGate: () => ClockGate) extends RawModule {

    val io = IO(new Bundle {
        val clocksIn = Input(Vec(n, Clock()))
        val clockOut = Output(Clock())
        val resetAsync = Input(AsyncReset())
        val sel = Input(UInt(log2Ceil(n).W))
    })

    val andClocks = io.clocksIn.map(x => ClockSignalNor2(ClockInverter(x), io.resetAsync.asBool))

    val syncs  = andClocks.map { c => withClockAndReset(c, io.resetAsync) { Module(new AsyncResetSynchronizerShiftReg(1, sync = depth, init = 0)) } }
    val gaters = andClocks.map { c =>
        val g = Module(genClockGate())
        g.io.in := c
        g.io.test_en := false.B
        g
    }

    syncs.zip(gaters).foreach { case (s, g) => g.io.en := s.io.q }

    syncs.zipWithIndex.foreach { case (s, i) => s.io.d := (io.sel === i.U) && !(syncs.zipWithIndex.filter(_._2 != i).map(_._1.io.q.asBool).reduce(_||_)) }

    io.clockOut := clockOrTree(gaters.map(_.io.out))(0)

    def clockOrTree(in: Seq[Clock]): Seq[Clock] = {
        if (in.length == 1) {
            return in
        } else {
            return clockOrTree(Seq.fill(in.length / 2)(Module(new ClockOr2))
                .zipWithIndex.map({ case (or, i) =>
                    or.io.clocksIn(0) := in(2*i)
                    or.io.clocksIn(1) := in(2*i+1)
                    or.io.clockOut
                }) ++ (if(in.length % 2 == 1) Seq(in.last) else Seq()))
        }
    }

}

object ClockMutexMux {

    private val defaultDepth = 3

    def apply(clocks: Seq[Clock], genClockGate: () => ClockGate, depth: Int): ClockMutexMux = {
        val mux = Module(new ClockMutexMux(clocks.length, depth, genClockGate))
        mux.io.clocksIn := VecInit(clocks)
        mux
    }

    def apply(clocks: Seq[Clock], genClockGate: () => ClockGate): ClockMutexMux = apply(clocks, genClockGate, defaultDepth)
    def apply(clocks: Seq[Clock])(implicit p: Parameters): ClockMutexMux = apply(clocks, defaultDepth)(p)
    def apply(clocks: Seq[Clock], depth: Int)(implicit p: Parameters): ClockMutexMux = apply(clocks, p(ClockGateImpl), depth)

}

// Programmable clock divider which divides by (N+1) (0 stops clock)
// This is fully synchronous and doesn't need any async resets
// The implicit clock of this thing is the fast one
class ClockDivider(width: Int, initDiv: Int = 0) extends Module {

    val io = IO(new Bundle {
        val divisor = Input(UInt(width.W))
        val clockOut = Output(Clock())
    })

    val clockReg = Module(new ClockFlop)

    val divisorReg = RegInit(initDiv.U(width.W))
    val count = RegInit(0.U(width.W))

    clockReg.io.clockIn := this.clock
    clockReg.io.d := count < ((divisorReg >> 1) +& 1.U)
    io.clockOut := clockReg.io.clockOut

    when (count === divisorReg) {
        count := 0.U
        // Only change divisorReg when we're done with a full period
        divisorReg := io.divisor
    } .otherwise {
        count := count + 1.U
    }

}

// Performs clock division when divisor >= 1, as done in ClockDivider
// When divisor is 0, pass through the clock
class ClockDivideOrPass(width: Int, depth: Int = 3, genClockGate: () => ClockGate) extends RawModule {
  val io = IO(new Bundle {
    val clockIn = Input(Clock())
    val divisor = Input(UInt(width.W)) // this may not be synchronous with clockIn
    val resetAsync = Input(AsyncReset())
    val clockOut = Output(Clock())

  })
  val divider = withClockAndReset(io.clockIn, io.resetAsync) { Module(new ClockDivider(width, initDiv=1)) }
  divider.io.divisor := withClockAndReset(io.clockIn, io.resetAsync) { SynchronizerShiftReg(Mux(io.divisor === 0.U, 1.U, io.divisor)) }

  val clock_mux = Module(new ClockMutexMux(2, depth, genClockGate))
  clock_mux.io.clocksIn(0) := divider.io.clockOut
  clock_mux.io.clocksIn(1) := io.clockIn
  clock_mux.io.sel := io.divisor === 0.U // the sel signal is synchronized internally in the ClockMutexMux
  clock_mux.io.resetAsync := io.resetAsync
  io.clockOut := clock_mux.io.clockOut
}

object withGatedClock {
    def apply[T](clock: Clock, enable: Bool, genClockGate: () => ClockGate)(block: => T): T = {
        val g = Module(genClockGate())
        g.io.en := enable
        g.io.in := clock
        g.io.test_en := true.B
        withClock(g.io.out)(block)
    }

    def apply[T](clock: Clock, enable: Bool)(block: => T)(implicit p: Parameters): T = {
      withGatedClock(clock, enable, p(ClockGateImpl))(block)
    }
}

object withGatedClockAndReset {
    def apply[T](clock: Clock, enable: Bool, reset: Reset, genClockGate: () => ClockGate)(block: => T): T = {
        val g = Module(genClockGate())
        g.io.en := enable
        g.io.in := clock
        g.io.test_en := true.B
        withClockAndReset(g.io.out, reset)(block)
    }

    def apply[T](clock: Clock, enable: Bool, reset: Reset)(block: => T)(implicit p: Parameters): T = {
      withGatedClockAndReset(clock, enable, reset, p(ClockGateImpl))(block)
    }
}
