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
import chipyard.iobinders.{GetSystemParameters, JTAGChipIO}

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

case class MultiHarnessBinders(c0: Int, c1: Int) extends Field[MultiHarnessBinderMap](MultiHarnessBinderMapDefault)

class MultiHarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](chip0: Int, chip1: Int, fn: => (T, T, S, Seq[U], Seq[U]) => Unit)
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
          ((c0: Any, c1: Any, th: HasHarnessSignalReferences, ports0: Seq[Data], ports1: Seq[Data]) => {
            val pts0 = ports0.map(_.asInstanceOf[U])
            val pts1 = ports1.map(_.asInstanceOf[U])
            (c0, c1, th) match {
              case (c0: T, c1: T, th: S) => fn(c0, c1, th, pts0, pts1)
              case _ =>
            }
          })
      )
    })
