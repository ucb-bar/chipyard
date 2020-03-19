package chipyard.chiptop

import chisel3._
import chipyard.{Top, System, SystemModule}

import scala.collection.mutable.{ArrayBuffer}

import chisel3.experimental.{Analog}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.{DebugIO, PSDIO, HasPeripheryDebugModuleImp}
import freechips.rocketchip.subsystem.{HasExtInterruptsModuleImp, CanHaveMasterAXI4MemPortModuleImp}
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp}
import sifive.blocks.devices.uart.{UARTPortIO, HasPeripheryUARTModuleImp}
import testchipip.{BlockDeviceIO, CanHavePeripheryBlockDeviceModuleImp, SerialIO, CanHavePeripherySerialModuleImp}
import icenet.{NICIOvonly, CanHavePeripheryIceNICModuleImp}
import tracegen.{HasTraceGenTilesModuleImp}
import chipyard.config.ConfigValName._

import barstools.iocell.chisel._

case object BuildSystem extends Field[Parameters => SystemModule[System]]((p: Parameters) => Module(LazyModule(new Top()(p)).suggestName("system").module))

abstract class BaseChipTop(implicit val p: Parameters) extends RawModule {

  val iocells = ArrayBuffer.empty[IOCell]

  val systemClock = Wire(Input(Clock()))
  val systemReset = Wire(Input(Bool()))

  val system = withClockAndReset(systemClock, systemReset) { p(BuildSystem)(p) }

  def connectReset(r: Bool): Unit

}

trait HasChipTopSimpleClockAndReset { this: BaseChipTop =>
  implicit val p: Parameters

  // TODO how to handle async vs sync reset?
  val (clock, systemClockIO) = IOCell.generateIOFromSignal(systemClock, Some("iocell_clock"))
  val (reset, systemResetIO) = IOCell.generateIOFromSignal(systemReset, Some("iocell_reset"))

  iocells ++= systemClockIO
  iocells ++= systemResetIO

  def connectReset(r: Bool) { reset := r }

}

// TODO add a reset catch and sync block?

trait CanHaveChipTopGPIO { this: BaseChipTop =>
  implicit val p: Parameters
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

trait CanHaveChipTopUART { this: BaseChipTop =>
  implicit val p: Parameters
  val uart: Seq[UARTPortIO] = system match {
    case system: HasPeripheryUARTModuleImp => {
      system.uart.zipWithIndex.map { case (uart, i) =>
        val (port, ios) = IOCell.generateIOFromSignal(uart, Some(s"iocell_uart_${i}"))
        iocells ++= ios
        port
      }
    }
    case _ => Seq()
  }
}

trait CanHaveChipTopBlockDevice { this: BaseChipTop =>
  implicit val p: Parameters
  val bdev: Option[BlockDeviceIO] = system match {
    case system: CanHavePeripheryBlockDeviceModuleImp => {
      system.bdev.map { bdev =>
        val (port, ios) = IOCell.generateIOFromSignal(bdev, Some("iocell_bdev"))
        iocells ++= ios
        port
      }
    }
    case _ => None
  }
}

trait CanHaveChipTopIceNIC { this: BaseChipTop =>
  implicit val p: Parameters
  val net: Option[NICIOvonly] = system match {
    case system: CanHavePeripheryIceNICModuleImp  => {
      system.net.map { net =>
        val (port, ios) = IOCell.generateIOFromSignal(net, Some("iocell_net"))
        iocells ++= ios
        port
      }
    }
    case _ => None
  }
}

trait CanHaveChipTopExtInterrupts { this: BaseChipTop =>
  implicit val p: Parameters

  val interrupts = system match {
    case system: HasExtInterruptsModuleImp => {
      val (port, ios) = IOCell.generateIOFromSignal(system.interrupts, Some("iocell_interrupts"))
      iocells ++= ios
      Some(port)
    }
    case _ => None
  }

  def tieOffInterrupts() { interrupts.foreach(_ := 0.U) }
}

trait CanHaveChipTopDebug { this: BaseChipTop =>
  implicit val p: Parameters

  val psd = system match {
    case system: HasPeripheryDebugModuleImp => {
      val (port, ios) = IOCell.generateIOFromSignal(system.psd, Some("iocell_psd"))
      iocells ++= ios
      Some(port)
    }
    case _ => None
  }

  val debug = system match {
    case system: HasPeripheryDebugModuleImp => {
      system.debug.map { d =>
        val (port, ios) = IOCell.generateIOFromSignal(d, Some("iocell_debug"))
        iocells ++= ios
        port
      }
    }
    case _ => None
  }

}

trait CanHaveChipTopSerial { this: BaseChipTop =>
  implicit val p: Parameters

  import testchipip.SerialAdapter

  val serial = system match {
    case system: CanHavePeripherySerialModuleImp => {
      system.serial.map { s =>
        val (port, ios) = IOCell.generateIOFromSignal(s, Some("iocell_serial"))
        iocells ++= ios
        Some(port)
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
      val (port, ios) = IOCell.generateIOFromSignal(system.success, Some("iocell_success"))
      iocells ++= ios
      port
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

class ChipTop(implicit p: Parameters) extends BaseChipTop()(p)
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
