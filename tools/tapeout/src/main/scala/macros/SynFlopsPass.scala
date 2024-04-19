// See LICENSE for license details.

package barstools.macros

import barstools.macros.Utils._
import firrtl.Utils.{one, zero}
import firrtl._
import firrtl.ir._
import firrtl.passes.MemPortUtils.memPortField

import scala.collection.mutable

class SynFlopsPass(synflops: Boolean, libs: Seq[Macro]) extends firrtl.passes.Pass {
  val extraMods: mutable.ArrayBuffer[Module] = scala.collection.mutable.ArrayBuffer.empty[Module]
  lazy val libMods: Map[String, Module] = libs.map { lib =>
    lib.src.name -> {
      val (dataType, dataWidth) = lib.src.ports.foldLeft(None: Option[BigInt])((res, port) =>
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
        case None       => (UIntType(IntWidth(lib.src.width)), lib.src.width)
        case Some(gran) => (UIntType(IntWidth(gran)), gran.intValue)
      }

      val maxDepth = firrtl.Utils.min(lib.src.depth, 1 << 26)

      // Change macro to be mapped onto to look like the below mem
      // by changing its depth, and width
      val lib_macro = new Macro(
        lib.src.copy(
          name = "split_" + lib.src.name,
          depth = maxDepth,
          width = dataWidth,
          ports = lib.src.ports.map(p =>
            p.copy(
              width = p.width.map(_ => dataWidth),
              depth = p.depth.map(_ => maxDepth),
              maskGran = p.maskGran.map(_ => dataWidth)
            )
          )
        )
      )
      val mod_macro = new MacroCompilerPass(None, None, None, None).compile(lib, lib_macro)
      val (real_mod, real_macro) = mod_macro.get

      val mem = DefMemory(
        NoInfo,
        "ram",
        dataType,
        maxDepth,
        1, // writeLatency
        1, // readLatency. This is possible because of VerilogMemDelays
        real_macro.readers.indices.map(i => s"R_$i"),
        real_macro.writers.indices.map(i => s"W_$i"),
        real_macro.readwriters.indices.map(i => s"RW_$i")
      )

      val readConnects = real_macro.readers.zipWithIndex.flatMap { case (r, i) =>
        val clock = portToExpression(r.src.clock.get)
        val address = portToExpression(r.src.address)
        val enable = (r.src.chipEnable, r.src.readEnable) match {
          case (Some(en_port), Some(re_port)) =>
            and(portToExpression(en_port), portToExpression(re_port))
          case (Some(en_port), None) => portToExpression(en_port)
          case (None, Some(re_port)) => portToExpression(re_port)
          case (None, None)          => one
        }
        val data = memPortField(mem, s"R_$i", "data")
        val read = data
        Seq(
          Connect(NoInfo, memPortField(mem, s"R_$i", "clk"), clock),
          Connect(NoInfo, memPortField(mem, s"R_$i", "addr"), address),
          Connect(NoInfo, memPortField(mem, s"R_$i", "en"), enable),
          Connect(NoInfo, WRef(r.src.output.get.name), read)
        )
      }

      val writeConnects = real_macro.writers.zipWithIndex.flatMap { case (w, i) =>
        val clock = portToExpression(w.src.clock.get)
        val address = portToExpression(w.src.address)
        val enable = (w.src.chipEnable, w.src.writeEnable) match {
          case (Some(en), Some(we)) =>
            and(portToExpression(en), portToExpression(we))
          case (Some(en), None) => portToExpression(en)
          case (None, Some(we)) => portToExpression(we)
          case (None, None)     => zero // is it possible?
        }
        val mask = w.src.maskPort match {
          case Some(m) => portToExpression(m)
          case None    => one
        }
        val data = memPortField(mem, s"W_$i", "data")
        val write = portToExpression(w.src.input.get)
        Seq(
          Connect(NoInfo, memPortField(mem, s"W_$i", "clk"), clock),
          Connect(NoInfo, memPortField(mem, s"W_$i", "addr"), address),
          Connect(NoInfo, memPortField(mem, s"W_$i", "en"), enable),
          Connect(NoInfo, memPortField(mem, s"W_$i", "mask"), mask),
          Connect(NoInfo, data, write)
        )
      }

      val readwriteConnects = real_macro.readwriters.zipWithIndex.flatMap { case (rw, i) =>
        val clock = portToExpression(rw.src.clock.get)
        val address = portToExpression(rw.src.address)
        val wmode = rw.src.writeEnable match {
          case Some(we) => portToExpression(we)
          case None     => zero // is it possible?
        }
        val wmask = rw.src.maskPort match {
          case Some(wm) => portToExpression(wm)
          case None     => one
        }
        val enable = (rw.src.chipEnable, rw.src.readEnable) match {
          case (Some(en), Some(re)) =>
            and(portToExpression(en), or(portToExpression(re), wmode))
          case (Some(en), None) => portToExpression(en)
          case (None, Some(re)) => or(portToExpression(re), wmode)
          case (None, None)     => one
        }
        val wdata = memPortField(mem, s"RW_$i", "wdata")
        val rdata = memPortField(mem, s"RW_$i", "rdata")
        val write = portToExpression(rw.src.input.get)
        val read = rdata
        Seq(
          Connect(NoInfo, memPortField(mem, s"RW_$i", "clk"), clock),
          Connect(NoInfo, memPortField(mem, s"RW_$i", "addr"), address),
          Connect(NoInfo, memPortField(mem, s"RW_$i", "en"), enable),
          Connect(NoInfo, memPortField(mem, s"RW_$i", "wmode"), wmode),
          Connect(NoInfo, memPortField(mem, s"RW_$i", "wmask"), wmask),
          Connect(NoInfo, WRef(rw.src.output.get.name), read),
          Connect(NoInfo, wdata, write)
        )
      }

      extraMods.append(real_macro.module(Block(mem +: (readConnects ++ writeConnects ++ readwriteConnects))))
      real_mod
    }
  }.toMap

  def run(c: Circuit): Circuit = {
    if (!synflops) c
    else c.copy(modules = c.modules.map(m => libMods.getOrElse(m.name, m)) ++ extraMods)
  }
}
