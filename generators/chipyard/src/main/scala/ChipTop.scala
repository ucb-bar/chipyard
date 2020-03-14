package chipyard.chiptop

import chisel3._
import chipyard.{Top, System, SystemModule}

//import chisel3.experimental.{DataMirror}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.{DebugIO, PSDIO, HasPeripheryDebugModuleImp}
import freechips.rocketchip.subsystem.{HasExtInterruptsModuleImp, CanHaveMasterAXI4MemPortModuleImp}
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp}
import sifive.blocks.devices.uart.{UARTPortIO, HasPeripheryUARTModuleImp}
import testchipip.{BlockDeviceIO, CanHavePeripheryBlockDeviceModuleImp, SerialIO, CanHavePeripherySerialModuleImp}
import icenet.{NICConfig, NICIOvonly, CanHavePeripheryIceNICModuleImp}
import tracegen.{HasTraceGenTilesModuleImp}
import chipyard.config.ConfigValName._

case object BuildSystem extends Field[Parameters => SystemModule[System]]((p: Parameters) => Module(LazyModule(new Top()(p)).suggestName("system").module))

abstract class BaseChipTop(implicit val p: Parameters) extends RawModule {

  val systemClock = Wire(Clock())
  val systemReset = Wire(Reset())

  val system = withClockAndReset(systemClock, systemReset) { p(BuildSystem)(p) }

  def connectReset(r: Bool): Unit

}

trait HasChipTopSimpleClockAndReset { this: BaseChipTop =>
  implicit val p: Parameters

  val clock = IO(Input(Clock()))
  // TODO how to handle async vs sync reset?
  val reset = IO(Input(Bool()))

  //this.systemClock := DigitalInputIO(clock)
  //this.systemReset := DigitalInputIO(reset)

  this.systemClock := clock
  this.systemReset := reset

  def connectReset(r: Bool) { reset := r }

}

trait CanHaveChipTopGPIO { this: BaseChipTop =>
  implicit val p: Parameters
  // TODO this becomes analog
  val gpio: Seq[Bool] = system match {
    case system: HasPeripheryGPIOModuleImp => {
      system.gpio.map(_.pins).map(_.map(_.i.ival)).flatten.map { pin =>
        // TODO sane naming
        val g = IO(Input(Bool()))
        pin := g
        g
      }
    }
    case _ => Seq()
  }
}

trait CanHaveChipTopUART { this: BaseChipTop =>
  implicit val p: Parameters
  val uart: Seq[UARTPortIO] = system match {
    case system: HasPeripheryUARTModuleImp => {
      system.uart.map { uart =>
        val io = IO(new UARTPortIO())
        io <> uart
        io
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
        val io = IO(new BlockDeviceIO)
        io <> bdev
        io
      }
    }
    case _ => None
  }
}

trait CanHaveChipTopIceNIC { this: BaseChipTop =>
  implicit val p: Parameters
  val nicConf = system match {
    case system: CanHavePeripheryIceNICModuleImp => system.nicConf
    case _ => NICConfig()
  }
  val net: Option[NICIOvonly] = system match {
    case system: CanHavePeripheryIceNICModuleImp  => {
      system.net.map { net =>
        val io = IO(new NICIOvonly)
        io <> net
        io
      }
    }
    case _ => None
  }
}

trait CanHaveChipTopExtInterrupts { this: BaseChipTop =>
  implicit val p: Parameters

  val interrupts = system match {
    case system: HasExtInterruptsModuleImp => {
      val io = IO(Input(UInt(system.interrupts.getWidth.W)))
      io <> system.interrupts
      Some(io)
    }
    case _ => None
  }

  def tieOffInterrupts() { interrupts.foreach(_ := 0.U) }
}

trait CanHaveChipTopDebug { this: BaseChipTop =>
  implicit val p: Parameters

  val psd = system match {
    case system: HasPeripheryDebugModuleImp => {
      val io = IO(new PSDIO()(system.p))
      io <> system.psd
      Some(io)
    }
    case _ => None
  }

  val debug = system match {
    case system: HasPeripheryDebugModuleImp => {
      system.debug.map { debug =>
        val io = IO(new DebugIO()(system.p))
        io <> debug
        io
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
        val io = IO(new SerialIO(SerialAdapter.SERIAL_IF_WIDTH))
        io <> s
        Some(io)
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
      val io = IO(Output(Bool()))
      io <> system.success
      io
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
  with CanHaveChipTopSerial
  with CanHaveChipTopBlockDevice
  with CanHaveChipTopIceNIC
  with CanHaveChipTopExtInterrupts
  with CanHaveChipTopDebug
  with CanHaveChipTopTraceGen
  with CanHaveChipTopAXI4Memory

