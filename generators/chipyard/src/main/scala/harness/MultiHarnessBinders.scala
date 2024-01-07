package chipyard.harness

import chisel3._
import chisel3.util._
import chisel3.experimental.{DataMirror, Direction}

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import testchipip._

import chipyard._
import chipyard.iobinders.{GetSystemParameters, JTAGChipIO, HasChipyardPorts, Port, SerialTLPort}

import scala.reflect.{ClassTag}

case class MultiHarnessBinders(chip0: Int, chip1: Int) extends Field[Seq[MultiHarnessBinderFunction]](Nil)

object ApplyMultiHarnessBinders {
  def apply(th: HasHarnessInstantiators, chips: Seq[LazyModule])(implicit p: Parameters): Unit = {
    Seq.tabulate(chips.size, chips.size) { case (i, j) => if (i != j) {
      (chips(i), chips(j)) match {
        case (l0: HasChipyardPorts, l1: HasChipyardPorts) => p(MultiHarnessBinders(i, j)).foreach { f =>
          f(th, l0.ports, l1.ports)
        }
      }
    }}
  }
}

class MultiHarnessBinder[T <: Port[_], S <: HasHarnessInstantiators](
  chip0: Int, chip1: Int,
  chip0portFn: T => Boolean, chip1portFn: T => Boolean,
  connectFn: (S, T, T) => Unit
)(implicit tag0: ClassTag[T], tag1: ClassTag[S]) extends Config((site, here, up) => {
    // Override any HarnessBinders for chip0/chip1
    case MultiChipParameters(`chip0`) => new Config(
      new HarnessBinder({case (th: S, port: T) if chip0portFn(port) => }) ++ up(MultiChipParameters(chip0))
    )
    case MultiChipParameters(`chip1`) => new Config(
      new HarnessBinder({case (th: S, port: T) if chip1portFn(port) => }) ++ up(MultiChipParameters(chip1))
    )
    // Set the multiharnessbinder key
    case MultiHarnessBinders(`chip0`, `chip1`) => up(MultiHarnessBinders(chip0, chip1)) :+ {
      ((th: S, chip0Ports: Seq[Port[_]], chip1Ports: Seq[Port[_]]) => {
        val chip0Port: Seq[T] = chip0Ports.collect { case (p: T) if chip0portFn(p) => p }
        val chip1Port: Seq[T] = chip1Ports.collect { case (p: T) if chip1portFn(p) => p }
        require(chip0Port.size == 1 && chip1Port.size == 1)
        connectFn(th, chip0Port(0), chip1Port(0))
      })
    }
  })


class WithMultiChipSerialTL(chip0: Int, chip1: Int, chip0portId: Int = 0, chip1portId: Int = 0) extends MultiHarnessBinder(
  chip0, chip1,
  (p0: SerialTLPort) => p0.portId == chip0portId,
  (p1: SerialTLPort) => p1.portId == chip1portId,
  (th: HasHarnessInstantiators, p0: SerialTLPort, p1: SerialTLPort) => {
    (DataMirror.directionOf(p0.io.clock), DataMirror.directionOf(p1.io.clock)) match {
      case (Direction.Input, Direction.Output) => p0.io.clock := p1.io.clock
      case (Direction.Output, Direction.Input) => p1.io.clock := p0.io.clock
    }
    p0.io.bits.in <> p1.io.bits.out
    p1.io.bits.in <> p0.io.bits.out
  }
)
