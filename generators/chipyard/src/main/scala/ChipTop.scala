package chipyard.chiptop

import chisel3._
import chipyard.{DigitalTop, System, SystemModule}

import scala.collection.mutable.{ArrayBuffer}

import chisel3.experimental.{Analog}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.{DebugIO, PSDIO, HasPeripheryDebugModuleImp}
import freechips.rocketchip.subsystem.{HasExtInterruptsModuleImp, CanHaveMasterAXI4MemPortModuleImp}
import sifive.blocks.devices.gpio.{GPIOPortIO, HasPeripheryGPIOModuleImp}
import sifive.blocks.devices.uart.{UARTPortIO, HasPeripheryUARTModuleImp}
import testchipip.{BlockDeviceIO, CanHavePeripheryBlockDeviceModuleImp, SerialIO, CanHavePeripherySerialModuleImp}
import icenet.{NICIOvonly, CanHavePeripheryIceNICModuleImp}
import tracegen.{HasTraceGenTilesModuleImp}
import chipyard.config.ConfigValName._

import barstools.iocell.chisel._

case object BuildSystem extends Field[Parameters => SystemModule[System]]((p: Parameters) => Module(LazyModule(new DigitalTop()(p)).suggestName("system").module))

abstract class BaseChipTop(val useIOCells: Boolean = true)(implicit val p: Parameters) extends RawModule {

  val iocells = ArrayBuffer.empty[IOCell]

  val systemClock = Wire(Input(Clock()))
  val systemReset = Wire(Input(Bool()))

  val system = withClockAndReset(systemClock, systemReset) { p(BuildSystem)(p) }

  def connectReset(r: Bool): Unit

}

trait HasChipTopSimpleClockAndReset { this: BaseChipTop =>
  implicit val p: Parameters

  val (clock, systemClockIO) = if (useIOCells) {
    IOCell.generateIOFromSignal(systemClock, Some("iocell_clock"))
  } else {
    val port = IO(Input(Clock()))
    systemClock := port
    (port, Seq())
  }

  // TODO how to handle async vs sync reset?
  val (reset, systemResetIO) = if (useIOCells) {
    IOCell.generateIOFromSignal(systemReset, Some("iocell_reset"))
  } else {
    val port = IO(Input(Bool()))
    systemReset := port
    (port, Seq())
  }

  iocells ++= systemClockIO
  iocells ++= systemResetIO

  def connectReset(r: Bool) { reset := r }

}

// TODO add a reset catch and sync block?

trait CanHaveChipTopGPIO { this: BaseChipTop =>
  implicit val p: Parameters

  require(useIOCells, "Cannot use CanHaveChipTopGPIO without IO cells")

  val gpio: Seq[Seq[Analog]] = system match {
    case system: HasPeripheryGPIOModuleImp => {
      system.gpio.map(_.pins).zipWithIndex.map { case (pins, i) =>
        pins.zipWithIndex.map { case (pin, j) =>
          val g = IO(Analog(1.W))
          // TODO functionalize this?
          val iocell = IOCell.exampleGPIO()
          iocell.suggestName(s"iocell_gpio_${i}_${j}")
          iocells += iocell
          iocell.io.o := pin.o.oval
          iocell.io.oe := pin.o.oe
          iocell.io.ie := pin.o.ie
          pin.i.ival := iocell.io.i
          iocell.io.pad <> g
          g
        }
      }
    }
    case _ => Seq()
  }
}

trait CanHaveChipTopGPIOConnections { this: BaseChipTop =>

  // TODO maybe this is overly restrictive?
  require(!useIOCells, "Doesn't make sense to use CanHaveChipTopGPIOConnections with IO cells")

  val gpio: Seq[GPIOPortIO] = system match {
    case system: HasPeripheryGPIOModuleImp => {
      system.gpio.map { g =>
        val port = IO(new GPIOPortIO(g.c))
        port <> g
        port
      }
    }
    case _ => Seq()
  }

}

trait CanHaveChipTopUART { this: BaseChipTop =>
  implicit val p: Parameters
  val uart: Seq[UARTPortIO] = system match {
    case system: HasPeripheryUARTModuleImp => {
      system.uart.zipWithIndex.map { case (u, i) =>
        if (useIOCells) {
          val (port, ios) = IOCell.generateIOFromSignal(u, Some(s"iocell_uart_${i}"))
          iocells ++= ios
          port
        } else {
          val port = IO(new UARTPortIO())
          port <> u
          port
        }
      }
    }
    case _ => Seq()
  }
}

trait CanHaveChipTopBlockDevice { this: BaseChipTop =>
  implicit val p: Parameters
  val bdev: Option[BlockDeviceIO] = system match {
    case system: CanHavePeripheryBlockDeviceModuleImp => {
      system.bdev.map { b =>
        if (useIOCells) {
          val (port, ios) = IOCell.generateIOFromSignal(b, Some("iocell_bdev"))
          iocells ++= ios
          port
        } else {
          val port = IO(new BlockDeviceIO)
          port <> b
          port
        }
      }
    }
    case _ => None
  }
}

trait CanHaveChipTopIceNIC { this: BaseChipTop =>
  implicit val p: Parameters
  val net: Option[NICIOvonly] = system match {
    case system: CanHavePeripheryIceNICModuleImp => {
      system.net.map { n =>
        if (useIOCells) {
          val (port, ios) = IOCell.generateIOFromSignal(n, Some("iocell_net"))
          iocells ++= ios
          port
        } else {
          val port = IO(new NICIOvonly)
          port <> n
          port
        }
      }
    }
    case _ => None
  }
}

trait CanHaveChipTopExtInterrupts { this: BaseChipTop =>
  implicit val p: Parameters

  val interrupts = system match {
    case system: HasExtInterruptsModuleImp => {
      if (useIOCells) {
        val (port, ios) = IOCell.generateIOFromSignal(system.interrupts, Some("iocell_interrupts"))
        iocells ++= ios
        Some(port)
      } else {
        val port = IO(Input(UInt(system.interrupts.getWidth.W)))
        system.interrupts <> port
        Some(port)
      }
    }
    case _ => None
  }

  def tieOffInterrupts() { interrupts.foreach(_ := 0.U) }
}

trait CanHaveChipTopDebug { this: BaseChipTop =>
  implicit val p: Parameters

  val psd = system match {
    case system: HasPeripheryDebugModuleImp => {
      if (useIOCells) {
        val (port, ios) = IOCell.generateIOFromSignal(system.psd, Some("iocell_psd"))
        iocells ++= ios
        Some(port)
      } else {
        val port = IO(new PSDIO()(system.p))
        system.psd <> port
        Some(port)
      }
    }
    case _ => None
  }

  val debug = system match {
    case system: HasPeripheryDebugModuleImp => {
      system.debug.map { d =>
        if (useIOCells) {
          val (port, ios) = IOCell.generateIOFromSignal(d, Some("iocell_debug"))
          iocells ++= ios
          port
        } else {
          val port = IO(new DebugIO()(system.p))
          d <> port
          port
        }
      }
    }
    case _ => None
  }

}

trait CanHaveChipTopSerial { this: BaseChipTop =>
  implicit val p: Parameters

  import testchipip.SerialAdapter

  val serial: Option[SerialIO] = system match {
    case system: CanHavePeripherySerialModuleImp => {
      system.serial.map { s =>
        if (useIOCells) {
          val (port, ios) = IOCell.generateIOFromSignal(s, Some("iocell_serial"))
          iocells ++= ios
          port
        } else {
          val port = IO(new SerialIO(SerialAdapter.SERIAL_IF_WIDTH))
          s <> port
          port
        }
      }
    }
    case _ => None
  }

  def connectSimSerial(clock: Clock, reset: Bool) = {
    serial.map { s =>
      SerialAdapter.connectSimSerial(s, clock, reset)
    }.getOrElse(false.B)
  }
  def tieoffSerial() = serial.foreach { s => SerialAdapter.tieoff(s) }
}


trait CanHaveChipTopTraceGen { this: BaseChipTop =>
  implicit val p: Parameters

  val success = system match {
    case system: HasTraceGenTilesModuleImp => {
      if (useIOCells) {
        val (port, ios) = IOCell.generateIOFromSignal(system.success, Some("iocell_success"))
        iocells ++= ios
        port
      } else {
        val port = IO(Output(Bool()))
        system.success <> port
        port
      }
    }
    case _ => false.B
  }

}

trait CanHaveChipTopAXI4Memory { this: BaseChipTop =>
  implicit val p: Parameters

  system match {
    case system: CanHaveMasterAXI4MemPortModuleImp => {
      withClockAndReset(system.clock, system.reset) { system.connectSimAXIMem() }
    }
  }
}

class ChipTop(implicit p: Parameters) extends BaseChipTop(true)(p)
  with HasChipTopSimpleClockAndReset
  with CanHaveChipTopGPIO
  with CanHaveChipTopUART
  with CanHaveChipTopBlockDevice
  with CanHaveChipTopIceNIC
  with CanHaveChipTopExtInterrupts
  with CanHaveChipTopDebug
  with CanHaveChipTopTraceGen
  with CanHaveChipTopAXI4Memory
  with CanHaveChipTopSerial

class ChipTopNoIOCells(implicit p: Parameters) extends BaseChipTop(false)(p)
  with HasChipTopSimpleClockAndReset
  with CanHaveChipTopGPIOConnections
  with CanHaveChipTopUART
  with CanHaveChipTopBlockDevice
  with CanHaveChipTopIceNIC
  with CanHaveChipTopExtInterrupts
  with CanHaveChipTopDebug
  with CanHaveChipTopTraceGen
  with CanHaveChipTopAXI4Memory
  with CanHaveChipTopSerial
