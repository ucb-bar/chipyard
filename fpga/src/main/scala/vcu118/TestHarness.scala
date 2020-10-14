package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem.{ExtMem, BaseSubsystem}
import freechips.rocketchip.tilelink._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import chipyard.fpga.vcu118.bringup.{BringupGPIOs, BringupUARTVCU118ShellPlacer, BringupSPIVCU118ShellPlacer, BringupI2CVCU118ShellPlacer, BringupGPIOVCU118ShellPlacer}
import chipyard.harness._
import chipyard.{HasHarnessSignalReferences, HasTestHarnessFunctions}

case object DUTFrequencyKey extends Field[Double](100.0)

class VCU118FPGATestHarness(override implicit val p: Parameters) extends ChipyardVCU118Shell with HasHarnessSignalReferences {

  def dp = designParameters

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  sys_clock.get() match {
    case Some(x : SysClockVCU118PlacedOverlay) => {
      harnessSysPLL := x.node
    }
  }

  // create and connect to the dutClock
  val dutClock = ClockSinkNode(freqMHz = dp(DUTFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  InModuleBody {
    topDesign.module match { case td: LazyModuleImp => {
        td.clock := dutClock.in.head._1.clock
        td.reset := dutClock.in.head._1.reset
      }
    }
  }

  // connect ref clock to dummy sink node
  ref_clock.get() match {
    case Some(x : RefClockVCU118PlacedOverlay) => {
      val sink = ClockSinkNode(Seq(ClockSinkParameters()))
      sink := x.node
    }
  }

  lazy val harnessClock = InModuleBody {
    dutClock.in.head._1.clock
  }.getWrappedValue
  lazy val harnessReset = InModuleBody {
    WireInit(dutClock.in.head._1.reset)
  }.getWrappedValue
  lazy val dutReset = harnessReset
  lazy val success = InModuleBody { false.B }.getWrappedValue

  topDesign match { case d: HasTestHarnessFunctions =>
    InModuleBody {
      d.harnessFunctions.foreach(_(this))
    }
    ApplyHarnessBinders(this, d.lazySystem, d.portMap.toMap)
  }
}

