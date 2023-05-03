package chipyard.harness

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule, DataMirror, Direction}

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import barstools.iocell.chisel._

import testchipip._

import chipyard._
import chipyard.clocking.{HasChipyardPRCI, ClockWithFreq}
import chipyard.iobinders.{GetSystemParameters, JTAGChipIO, HasIOBinders}

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

case class MultiHarnessBinders(c0: Int, c1: Int) extends Field[MultiHarnessBinderMap](MultiHarnessBinderMapDefault)

class MultiHarnessBinder[T, S <: HasChipyardHarnessInstantiators, U <: Data](chip0: Int, chip1: Int, fn: => (T, T, S, Seq[U], Seq[U]) => Unit)
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends Config((site, here, up) => {
      // Override any HarnessBinders for chip0/chip1
      case MultiChipParameters(`chip0`) => new Config(
        new OverrideHarnessBinder[T, S, U]((system: T, th: S, ports: Seq[U]) => Nil) ++
        up(MultiChipParameters(chip0))
      )
      case MultiChipParameters(`chip1`) => new Config(
        new OverrideHarnessBinder[T, S, U]((system: T, th: S, ports: Seq[U]) => Nil) ++
        up(MultiChipParameters(chip1))
      )
      // Set the multiharnessbinder key
      case MultiHarnessBinders(`chip0`, `chip1`) => up(MultiHarnessBinders(chip0, chip1)) +
        (tag.runtimeClass.toString ->
          ((c0: Any, c1: Any, th: HasChipyardHarnessInstantiators, ports0: Seq[Data], ports1: Seq[Data]) => {
            val pts0 = ports0.map(_.asInstanceOf[U])
            val pts1 = ports1.map(_.asInstanceOf[U])
            require(pts0.size == pts1.size)
            (c0, c1, th) match {
              case (c0: T, c1: T, th: S) => fn(c0, c1, th, pts0, pts1)
              case _ =>
            }
          })
      )
    })

object ApplyMultiHarnessBinders {
  def apply(th: HasChipyardHarnessInstantiators, chips: Seq[LazyModule])(implicit p: Parameters): Unit = {
    Seq.tabulate(chips.size, chips.size) { case (i, j) => if (i != j) {
      (chips(i), chips(j)) match {
        case (l0: HasIOBinders, l1: HasIOBinders) => p(MultiHarnessBinders(i, j)).foreach {
          case (s, f) => {
            f(l0.lazySystem       , l1.lazySystem       , th, l0.portMap(s), l1.portMap(s))
            f(l0.lazySystem.module, l1.lazySystem.module, th, l0.portMap(s), l1.portMap(s))
          }
        }
        case _ =>
      }
    }}
  }
}

class WithMultiChipSerialTL(chip0: Int, chip1: Int) extends MultiHarnessBinder(chip0, chip1, (
  (system0: CanHavePeripheryTLSerial, system1: CanHavePeripheryTLSerial,
    th: HasChipyardHarnessInstantiators,
    ports0: Seq[ClockedIO[SerialIO]], ports1: Seq[ClockedIO[SerialIO]]
  ) => {
    require(ports0.size == ports1.size)
    (ports0 zip ports1).map { case (l, r) =>
      l.clock <> r.clock
      require(l.bits.w == r.bits.w)
      l.bits.flipConnect(r.bits)
    }
  }
))
