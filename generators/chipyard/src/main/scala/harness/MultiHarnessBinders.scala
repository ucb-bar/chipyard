package chipyard.harness

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import testchipip._

import chipyard._
import chipyard.clocking.{HasChipyardPRCI, ClockWithFreq}
import chipyard.iobinders.{GetSystemParameters, JTAGChipIO, HasIOBinders}

import scala.reflect.{ClassTag}

case class MultiHarnessBinders(c0: Int, c1: Int) extends Field[MultiHarnessBinderMap](MultiHarnessBinderMapDefault)

class MultiHarnessBinder[T0, T1, S <: HasHarnessInstantiators, U0 <: Data, U1 <: Data]
  (chip0: Int, chip1: Int, fn: => (T0, T1, S, Seq[U0], Seq[U1]) => Unit)
  (implicit tag0: ClassTag[T0], tag1: ClassTag[T1], thtag: ClassTag[S], ptag0: ClassTag[U0], ptag1: ClassTag[U1])
    extends Config((site, here, up) => {
      // Override any HarnessBinders for chip0/chip1
      case MultiChipParameters(`chip0`) => new Config(
        new OverrideHarnessBinder[T0, S, U0]((system: T0, th: S, ports: Seq[U0]) => Nil) ++
        up(MultiChipParameters(chip0))
      )
      case MultiChipParameters(`chip1`) => new Config(
        new OverrideHarnessBinder[T1, S, U1]((system: T1, th: S, ports: Seq[U1]) => Nil) ++
        up(MultiChipParameters(chip1))
      )
      // Set the multiharnessbinder key
      case MultiHarnessBinders(`chip0`, `chip1`) => up(MultiHarnessBinders(chip0, chip1)) +
        ((tag0.runtimeClass.toString, tag1.runtimeClass.toString) ->
          ((c0: Any, c1: Any, th: HasHarnessInstantiators, ports0: Seq[Data], ports1: Seq[Data]) => {
            val pts0 = ports0.map(_.asInstanceOf[U0])
            val pts1 = ports1.map(_.asInstanceOf[U1])
            require(pts0.size == pts1.size)
            (c0, c1, th) match {
              case (c0: T0, c1: T1, th: S) => fn(c0, c1, th, pts0, pts1)
              case _ =>
            }
          })
      )
    })

object ApplyMultiHarnessBinders {
  def apply(th: HasHarnessInstantiators, chips: Seq[LazyModule])(implicit p: Parameters): Unit = {
    Seq.tabulate(chips.size, chips.size) { case (i, j) => if (i != j) {
      (chips(i), chips(j)) match {
        case (l0: HasIOBinders, l1: HasIOBinders) => p(MultiHarnessBinders(i, j)).foreach {
          case ((s0, s1), f) => {
            f(l0.lazySystem       , l1.lazySystem       , th, l0.portMap(s0), l1.portMap(s1))
            f(l0.lazySystem.module, l1.lazySystem.module, th, l0.portMap(s0), l1.portMap(s1))
          }
        }
        case _ =>
      }
    }}
  }
}

class WithMultiChipSerialTL(chip0: Int, chip1: Int) extends MultiHarnessBinder(chip0, chip1, (
  (system0: CanHavePeripheryTLSerial, system1: CanHavePeripheryTLSerial,
    th: HasHarnessInstantiators,
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
