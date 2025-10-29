package testchipip.serdes

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config._

class Flit(val flitWidth: Int) extends Bundle {
  val flit = UInt(flitWidth.W)
}

class Phit(val phitWidth: Int) extends Bundle {
  val phit = UInt(phitWidth.W)
}

class DecoupledPhitIO(val phitWidth: Int) extends Bundle {
  val in = Flipped(Decoupled(new Phit(phitWidth)))
  val out = Decoupled(new Phit(phitWidth))
}

class DecoupledFlitIO(val flitWidth: Int) extends Bundle {
  val in = Flipped(Decoupled(new Flit(flitWidth)))
  val out = Decoupled(new Flit(flitWidth))
}


trait HasClockOut { this: Bundle =>
  val clock_out = Output(Clock())
}

trait HasClockIn { this: Bundle =>
  val clock_in = Input(Clock())
}

// A decoupled flow-control serial interface where all signals are synchronous to
// a locally-produced clock
class DecoupledInternalSyncPhitIO(phitWidth: Int) extends DecoupledPhitIO(phitWidth) with HasClockOut

// A decoupled flow-control serial interface where all signals are synchronous to
// an externally produced clock
class DecoupledExternalSyncPhitIO(phitWidth: Int) extends DecoupledPhitIO(phitWidth) with HasClockIn

class ValidPhitIO(val phitWidth: Int) extends Bundle {
  val in = Input(Valid(new Phit(phitWidth)))
  val out = Output(Valid(new Phit(phitWidth)))
}

class ValidFlitIO(val flitWidth: Int) extends Bundle {
  val in = Input(Valid(new Flit(flitWidth)))
  val out = Output(Valid(new Flit(flitWidth)))
}

// A credited flow-control serial interface where all signals are synchronous to
// a slock provided by the transmitter of that signal
class CreditedSourceSyncPhitIO(phitWidth: Int) extends ValidPhitIO(phitWidth) {
  val clock_in = Input(Clock())
  val reset_out = Output(AsyncReset())
  val clock_out = Output(Clock())
  val reset_in = Input(AsyncReset())
}

class SerialIO(val w: Int) extends Bundle {
  val in = Flipped(Decoupled(UInt(w.W)))
  val out = Decoupled(UInt(w.W))

  def flipConnect(other: SerialIO) {
    in <> other.out
    other.in <> out
  }
}
