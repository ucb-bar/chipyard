// See LICENSE for license details.

package barstools.tapeout.transforms.macros

import firrtl._
import firrtl.ir._
import firrtl.Utils._
import firrtl.passes.MemPortUtils.{memPortField, memType}
import Utils._

class SynFlopsPass(synflops: Boolean, libs: Seq[Macro]) extends firrtl.passes.Pass {
  lazy val libMods = (libs map { lib => lib.name -> {
    val dataType = (lib.ports foldLeft (None: Option[BigInt]))((res, port) =>
      (res, port.maskName) match {
        case (_, None) =>
          res
        case (None, Some(_)) =>
          Some(port.effectiveMaskGran)
        case (Some(x), Some(_)) =>
          assert(x == port.effectiveMaskGran)
          res
      }
    ) match {
      case None => UIntType(IntWidth(lib.width))
      case Some(gran) => VectorType(UIntType(IntWidth(gran)), (lib.width / gran).toInt)
    }

    val mem = DefMemory(
      NoInfo,
      "ram",
      dataType,
      lib.depth.toInt,
      1, // writeLatency
      0, // readLatency
      (lib.readers ++ lib.readwriters).indices map (i => s"R_$i"),
      (lib.writers ++ lib.readwriters).indices map (i => s"W_$i"),
      Nil
    )

    val readConnects = (lib.readers ++ lib.readwriters).zipWithIndex flatMap { case (r, i) =>
      val clock = invert(WRef(r.clockName), r.clockPolarity)
      val address = invert(WRef(r.addressName), r.addressPolarity)
      val enable = (r.chipEnableName, r.readEnableName) match {
        case (Some(en), Some(re)) =>
          and(invert(WRef(en), r.chipEnablePolarity),
              invert(WRef(re), r.readEnablePolarity))
        case (Some(en), None) => invert(WRef(en), r.chipEnablePolarity)
        case (None, Some(re)) => invert(WRef(re), r.readEnablePolarity)
        case (None, None) => one
      }
      val data = memPortField(mem, s"R_$i", "data")
      val read = (dataType: @unchecked) match {
        case VectorType(tpe, size) => cat(((0 until size) map (k =>
          WSubIndex(data, k, tpe, UNKNOWNGENDER))).reverse)
        case _: UIntType => data
      }
      val addrReg = WRef(s"R_${i}_addr_reg", r.addrType, RegKind)
      Seq(
        DefRegister(NoInfo, addrReg.name, r.addrType, clock, zero, addrReg),
        Connect(NoInfo, memPortField(mem, s"R_$i", "clk"), clock),
        Connect(NoInfo, memPortField(mem, s"R_$i", "addr"), addrReg),
        Connect(NoInfo, memPortField(mem, s"R_$i", "en"), enable),
        Connect(NoInfo, WRef(r.outputName.get), read),
        Connect(NoInfo, addrReg, Mux(enable, address, addrReg, UnknownType))
      )
    }

    val writeConnects = (lib.writers ++ lib.readwriters).zipWithIndex flatMap { case (w, i) =>
      val clock = invert(WRef(w.clockName), w.clockPolarity)
      val address = invert(WRef(w.addressName), w.addressPolarity)
      val enable = (w.chipEnableName, w.writeEnableName) match {
        case (Some(en), Some(we)) =>
          and(invert(WRef(en), w.chipEnablePolarity),
              invert(WRef(we), w.writeEnablePolarity))
        case (Some(en), None) => invert(WRef(en), w.chipEnablePolarity)
        case (None, Some(we)) => invert(WRef(we), w.writeEnablePolarity)
        case (None, None) => zero // is it possible?
      }
      val mask = memPortField(mem, s"W_$i", "mask")
      val data = memPortField(mem, s"W_$i", "data")
      val write = invert(WRef(w.inputName.get), w.inputPolarity)
      Seq(
        Connect(NoInfo, memPortField(mem, s"W_$i", "clk"), clock),
        Connect(NoInfo, memPortField(mem, s"W_$i", "addr"), address),
        Connect(NoInfo, memPortField(mem, s"W_$i", "en"), enable)
      ) ++ (dataType match {
        case VectorType(tpe, size) =>
          val width = bitWidth(tpe).toInt
          ((0 until size) map (k =>
            Connect(NoInfo, WSubIndex(data, k, tpe, UNKNOWNGENDER),
                            bits(write, (k + 1) * width - 1, k * width)))) ++
          ((0 until size) map (k =>
            Connect(NoInfo, WSubIndex(mask, k, BoolType, UNKNOWNGENDER),
                            bits(WRef(w.maskName.get), k))))
        case _: UIntType =>
          Seq(Connect(NoInfo, data, write), Connect(NoInfo, mask, one))
      })
    }
    lib.module(Block(mem +: (readConnects ++ writeConnects)))
  }}).toMap

  def run(c: Circuit): Circuit = {
    if (!synflops) c
    else {
      val circuit = c.copy(modules = (c.modules map (m => libMods getOrElse (m.name, m))))
      // print(circuit.serialize)
      circuit
    }
  }
}
