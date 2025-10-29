package testchipip.test

import chisel3._
import chisel3.util._
import chisel3.experimental.IntParam
import freechips.rocketchip.unittest._
import freechips.rocketchip.util.{ResetCatchAndSync, EICG_wrapper}
import testchipip.clocking._

class ClockMutexMuxTest(timeout: Int = 200000) extends UnitTest(timeout) {

    val sReset :: sOn :: sOff :: sFinished :: Nil = Enum(4)

    val freqs = Seq(120, 350, 1000, 1250, 5000)
    val clocks = freqs.map(x => Module(new ClockGenerator(x)).io.clock)
    val monitors = freqs.map(x => Module(new PeriodMonitor(x, Some(x))))

    val mux = ClockMutexMux(clocks, () => new EICG_wrapper)
    mux.io.resetAsync := this.reset

    monitors.foreach(_.io.clock := mux.io.clockOut)
    val minMonitor = Module(new PeriodMonitor(120))
    minMonitor.io.clock := mux.io.clockOut
    minMonitor.io.enable := true.B

    val syncReset = ResetCatchAndSync(clocks(0), this.reset.asBool)

    val state = withClockAndReset(clocks(0), syncReset) { RegInit(sReset) }

    withClock(mux.io.clockOut) {
        assert(!this.reset.asBool, "Should not get any clock edges while in reset")
    }

    withClockAndReset(clocks(0), syncReset) {
        val counter = Counter(200)
        val sel = Counter(freqs.length)
        mux.io.sel := sel.value
        monitors.zipWithIndex.foreach { case (m,i) => m.io.enable := (state === sOn) && (sel.value === i.U) }

        when (state === sReset) {
            state := sOn
        } .elsewhen (state === sOn) {
            when (counter.inc()) {
                when (sel.inc()) {
                    state := sFinished
                } .otherwise {
                    state := sOff
                }
            }
        } .elsewhen (state === sOff) {
            when (counter.inc()) {
                state := sOn
            }
        }
    }

    io.finished := state === sFinished

}

class ClockDividerTest(timeout: Int = 200000) extends UnitTest(timeout) {

    val sReset :: sOn :: sOff :: sFinished :: Nil = Enum(4)

    val divs = List(0, 1, 2, 7, 3, 4)
    val base = 100

    val myClock = Module(new ClockGenerator(base)).io.clock
    val monitors = divs.map(x => if (x == 0) 0 else (x + 1)*base).map(x => Module(new PeriodMonitor(x, Some(x))))

    val syncReset = ResetCatchAndSync(myClock, this.reset.asBool)

    val divider = withClock(myClock) { Module(new ClockDivider(log2Ceil(divs.max))) }

    monitors.foreach { _.io.clock := divider.io.clockOut }

    val state = withClockAndReset(myClock, syncReset) { RegInit(sReset) }

    withClockAndReset(myClock, syncReset) {
        val counter = Counter(200)
        val div = Counter(divs.length)
        divider.io.divisor := VecInit(divs.map(_.U))(div.value)
        monitors.zipWithIndex.foreach { case (m,i) => m.io.enable := (state === sOn) && (div.value === i.U) }

        when (state === sReset) {
            state := sOn
        } .elsewhen (state === sOn) {
            when (counter.inc()) {
                when (div.inc()) {
                    state := sFinished
                } .otherwise {
                    state := sOff
                }
            }
        } .elsewhen (state === sOff) {
            when (counter.inc()) {
                state := sOn
            }
        }
    }

    io.finished := state === sFinished

}

class PeriodMonitor(minperiodps: Int, maxperiodps: Option[BigInt] = None) extends BlackBox(Map(
    "minperiodps" -> IntParam(minperiodps),
    "maxperiodps" -> IntParam(maxperiodps.getOrElse(BigInt(0)))
)) {

    val io = IO(new Bundle {
        val clock = Input(Clock())
        val enable = Input(Bool())
    })

}

class ClockGenerator(periodps: Int) extends BlackBox(Map("periodps" -> IntParam(periodps))) {

    val io = IO(new Bundle {
        val clock = Output(Clock())
    })

}

object ClockUtilTests {

    def apply(): Seq[UnitTest] = Seq(Module(new ClockMutexMuxTest), Module(new ClockDividerTest))

}

