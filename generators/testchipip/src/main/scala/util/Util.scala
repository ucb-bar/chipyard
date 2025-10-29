package testchipip.util

import chisel3._
import chisel3.util._
import chisel3.reflect.DataMirror
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy.{IdRange, ValName, LazyModule, LazyModuleImp}
import freechips.rocketchip.util.AsyncResetReg
import freechips.rocketchip.tilelink._

// a counter that clock gates most of its MSBs using the LSB carry-out
// uses asyncresetregs to make it easy for cross-clock domain work
case class AsyncWideCounter(width: Int, inc: UInt = 1.U, reset: Boolean = true)
{
  private val isWide = width > 2*inc.getWidth
  private val smallWidth = if (isWide) inc.getWidth max log2Up(width) else width
  private val widerNextSmall = Wire(UInt((smallWidth + 1).W))
  private val nextSmall = Wire(UInt(smallWidth.W))
  private val small = if (reset) AsyncResetReg(nextSmall, 0, "smallReg") else AsyncResetReg(nextSmall, "smallReg")
  widerNextSmall := small +& inc
  nextSmall := widerNextSmall

  private val large = if (isWide) {
    val nextR = Wire(UInt((width - smallWidth).W))
    val r = if (reset) AsyncResetReg(nextR, 0, "rReg") else AsyncResetReg(nextR, "rReg")
    when (widerNextSmall(smallWidth)) {
      nextR := r +& 1.U
    }.otherwise {
      nextR := r
    }
    r
  } else null

  val value = if (isWide) large ## small else small
  lazy val carryOut = {
    val lo = (small ^ widerNextSmall) >> 1
    if (!isWide) lo else {
      val hi = Mux(widerNextSmall(smallWidth), large ^ (large +& 1.U), 0.U) >> 1
      hi ## lo
    }
  }
}

// As WideCounter, but it's a module so it can take arbitrary clocks
class WideCounterModule(w: Int, inc: UInt = 1.U, rst: Boolean = true, clockSignal: Clock = null, resetSignal: Bool = null)
    extends Module {
  val io = IO(new Bundle {
    val value = Output(UInt(w.W))
  })
  Option(clockSignal).foreach(clock := _)
  Option(resetSignal).foreach(reset := _)
  io.value := AsyncWideCounter(w, inc, rst).value
}

object WideCounterModule {
  def apply(w: Int, c: Clock, r: Bool) = {
    val counter = Module(new WideCounterModule(w, clockSignal = c, resetSignal = r))
    counter.suggestName("wideCounterInst")
    counter.io.value
  }
  def apply(w: Int, c: Clock) = {
    val counter = Module(new WideCounterModule(w, clockSignal = c))
    counter.suggestName("wideCounterInst")
    counter.io.value
  }
}

// Use gray coding to safely synchronize a word across a clock crossing.
// This should be placed in the receiver's clock domain.
class WordSync[T <: Data](gen: T, lat: Int = 2) extends Module {
  val size = gen.getWidth
  val io = IO(new Bundle {
    val in = Flipped(chiselTypeOf(gen))
    val out = chiselTypeOf(gen)
    val tx_clock = Input(Clock())
  })
  val bin2gray = Module(new BinToGray(gen,io.tx_clock))
  bin2gray.io.bin := io.in
  val out_gray = ShiftRegister(bin2gray.io.gray, lat)
  io.out := (
    (0 until size)
      .map(out_gray.asUInt >> _.U)
      .reduceLeft((a: UInt, b: UInt) => a^b)).asTypeOf(gen)
}

class BinToGray[T <: Data](gen: T, c: Clock) extends Module {
  val io = IO(new Bundle {
    val bin = Flipped(chiselTypeOf(gen))
    val gray = UInt(gen.getWidth.W)
  })
  clock := c
  io.gray := RegNext(io.bin.asUInt ^ (io.bin.asUInt >> 1.U))
}

object WordSync {
  def apply[T <: Data](word: T, c: Clock) = {
    val sync = Module(new WordSync(word))
    sync.suggestName("wordSyncInst")
    sync.io.tx_clock := c
    sync.io.in := word
    sync.io.out
  }
  def apply[T <: Data](gen: T, word: Data, c: Clock, lat: Int = 2) = {
    val sync = Module(new WordSync(gen,lat))
    sync.suggestName("wordSyncInst")
    sync.io.tx_clock := c
    sync.io.in := word
    sync.io.out
  }
}

class DecoupledMux[T <: Data](typ: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, Decoupled(typ)))
    val out = Decoupled(typ)
    val sel = Input(UInt(log2Ceil(n).W))
  })

  if (n > 1) {
    io.out.valid := io.in(io.sel).valid
    io.out.bits := io.in(io.sel).bits
    io.in.zipWithIndex.foreach { case (in, i) =>
      in.ready := io.out.ready && io.sel === i.U
    }
  } else { io.out <> io.in.head }
}

object DecoupledMux {
  def apply[T <: Data](sel: UInt, in: Seq[DecoupledIO[T]]): DecoupledIO[T] = {
    val mux = Module(new DecoupledMux(in(0).bits.cloneType, 2))
    mux.io.sel := sel
    mux.io.in <> in
    mux.io.out
  }

  def apply[T <: Data](sel: Bool, a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] =
    apply(sel, Seq(b, a))
}

class ClockedIO[T <: Data](private val gen: T) extends Bundle {
  val clock = Output(Clock())
  val bits = DataMirror.internal.chiselTypeClone[T](gen)
}

class ClockedAndResetIO[T <: Data](private val gen: T) extends Bundle {
  val clock = Output(Clock())
  val reset = Output(Reset())
  val bits = DataMirror.internal.chiselTypeClone[T](gen)
}

class TLSinkSetter(endSinkId: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLAdapterNode(managerFn = { m => m.v1copy(endSinkId = endSinkId) })
  lazy val module = new LazyModuleImp(this) {
    // FIXME: bulk connect
    def connect[T <: TLBundleBase](out: DecoupledIO[T], in: DecoupledIO[T]) {
      out.valid := in.valid
      out.bits := in.bits
      in.ready := out.ready
    }

    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      connect(out.a, in.a) // out.a <> in .a
      connect(in.d, out.d) // in .d <> out.d
      if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
        connect(in.b, out.b) // in .b <> out.b
        connect(out.c, in.c) // out.c <> in .c
        connect(out.e, in.e) // out.e <> in .e
      } else {
        in.b.valid := false.B
        in.c.ready := true.B
        in.e.ready := true.B
        out.b.ready := true.B
        out.c.valid := false.B
        out.e.valid := false.B
      }
    }
  }
}

object TLSinkSetter {
  def apply(endSinkId: Int)(implicit p: Parameters): TLNode = {
    val widener = LazyModule(new TLSinkSetter(endSinkId))
    widener.node
  }
}

class TLSourceSetter(sourceId: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLAdapterNode(clientFn = { cp => cp.v1copy(clients = cp.clients.map { c => c.v1copy(sourceId = IdRange(0, sourceId))} )})
  lazy val module = new LazyModuleImp(this) {
    // FIXME: bulk connect
    def connect[T <: TLBundleBase](out: DecoupledIO[T], in: DecoupledIO[T]) {
      out.valid := in.valid
      out.bits := in.bits
      in.ready := out.ready
    }

    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      connect(out.a, in.a) // out.a <> in .a
      connect(in.d, out.d) // in .d <> out.d
      if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
        connect(in.b, out.b) // in .b <> out.b
        connect(out.c, in.c) // out.c <> in .c
        connect(out.e, in.e) // out.e <> in .e
      } else {
        in.b.valid := false.B
        in.c.ready := true.B
        in.e.ready := true.B
        out.b.ready := true.B
        out.c.valid := false.B
        out.e.valid := false.B
      }
    }
  }
}

object TLSourceSetter {
  def apply(sourceId: Int)(implicit p: Parameters): TLNode = {
    val widener = LazyModule(new TLSourceSetter(sourceId))
    widener.node
  }
}
