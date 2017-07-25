// See LICENSE for license details.

package barstools.macros

import firrtl._
import firrtl.ir._
import firrtl.Utils._
import firrtl.passes.MemPortUtils.{memPortField, memType}
import Utils._

class SynFlopsPass(synflops: Boolean, libs: Seq[Macro]) extends firrtl.passes.Pass {
  lazy val libMods = (libs map { lib => lib.src.name -> {
    val dataType = (lib.src.ports foldLeft (None: Option[BigInt]))((res, port) =>
      (res, port.maskPort) match {
        case (_, None) =>
          res
        case (None, Some(_)) =>
          Some(port.effectiveMaskGran)
        case (Some(x), Some(_)) =>
          assert(x == port.effectiveMaskGran)
          res
      }
    ) match {
      case None => UIntType(IntWidth(lib.src.width))
      case Some(gran) => VectorType(UIntType(IntWidth(gran)), (lib.src.width / gran).toInt)
    }

    val mem = DefMemory(
      NoInfo,
      "ram",
      dataType,
      lib.src.depth,
      1, // writeLatency
      0, // readLatency
      (lib.readers ++ lib.readwriters).indices map (i => s"R_$i"),
      (lib.writers ++ lib.readwriters).indices map (i => s"W_$i"),
      Nil
    )

    val readConnects = (lib.readers ++ lib.readwriters).zipWithIndex flatMap { case (r, i) =>
      val clock = portToExpression(r.src.clock)
      val address = portToExpression(r.src.address)
      val enable = (r.src chipEnable, r.src readEnable) match {
        case (Some(en_port), Some(re_port)) =>
          and(portToExpression(en_port),
              portToExpression(re_port))
        case (Some(en_port), None) => portToExpression(en_port)
        case (None, Some(re_port)) => portToExpression(re_port)
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
        Connect(NoInfo, WRef(r.src.output.get.name), read),
        Connect(NoInfo, addrReg, Mux(enable, address, addrReg, UnknownType))
      )
    }

    val writeConnects = (lib.writers ++ lib.readwriters).zipWithIndex flatMap { case (w, i) =>
      val clock = portToExpression(w.src.clock)
      val address = portToExpression(w.src.address)
      val enable = (w.src.chipEnable, w.src.writeEnable) match {
        case (Some(en), Some(we)) =>
          and(portToExpression(en),
              portToExpression(we))
        case (Some(en), None) => portToExpression(en)
        case (None, Some(we)) => portToExpression(we)
        case (None, None) => zero // is it possible?
      }
      val mask = memPortField(mem, s"W_$i", "mask")
      val data = memPortField(mem, s"W_$i", "data")
      val write = portToExpression(w.src.input.get)
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
                            bits(WRef(w.src.maskPort.get.name), k))))
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
