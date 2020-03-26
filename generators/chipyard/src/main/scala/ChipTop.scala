package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{LazyModule}
import chipyard.config.ConfigValName._
import chipyard.iobinders.{IOBinders}
import chipyard.iobinders.types.{TestHarnessFunction}

import barstools.iocell.chisel._

case object BuildSystem extends Field[Parameters => SystemModule[System]]((p: Parameters) => Module(LazyModule(new DigitalTop()(p)).suggestName("system").module))

abstract class BaseChipTop()(implicit val p: Parameters) extends RawModule with HasTestHarnessFunctions {

  val iocells = ArrayBuffer.empty[IOCell]
  val harnessFunctions = ArrayBuffer.empty[TestHarnessFunction]

  val systemClock = Wire(Input(Clock()))
  val systemReset = Wire(Input(Bool()))

  val system = withClockAndReset(systemClock, systemReset) { p(BuildSystem)(p) }

  private val (_ports, _iocells, _harnessFunctions) = p(IOBinders).values.map(_(system)).flatten.unzip3

  // We ignore _ports for now...
  iocells ++= _iocells.flatten
  harnessFunctions ++= _harnessFunctions.flatten

}

trait HasChipTopSimpleClockAndReset { this: BaseChipTop =>
  implicit val p: Parameters

  val (clock, systemClockIO) = IOCell.generateIOFromSignal(systemClock, Some("iocell_clock"))

  // TODO add a reset catch and sync block?
  // TODO how to handle async vs sync reset?
  val (reset, systemResetIO) = IOCell.generateIOFromSignal(systemReset, Some("iocell_reset"))

  iocells ++= systemClockIO
  iocells ++= systemResetIO

  harnessFunctions += { (th: TestHarness) => {
    // Connect clock; it's not done implicitly with RawModule
    clock := th.c
    // Connect reset; it's not done implicitly with RawModule
    reset := th.r
    Seq()
  } }

}

class ChipTop()(implicit p: Parameters) extends BaseChipTop()(p)
  with HasChipTopSimpleClockAndReset
