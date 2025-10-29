package testchipip.spi

import chisel3._
import chisel3.util.{HasBlackBoxResource}
import chisel3.experimental.{Analog, IntParam, StringParam}

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.util.{PlusArgArtefacts}
import sifive.blocks.devices.spi.{PeripherySPIFlashKey, SPIFlashParams}

class SPIChipIO(val csWidth: Int = 1) extends Bundle {
  val sck = Output(Bool())
  val cs = Vec(csWidth, Output(Bool()))
  val dq = Vec(4, Analog(1.W)) // Not using Analog(4.W) because we can't connect these to IO cells
}

class SPIFlashIO extends SPIChipIO(1) {
  val reset = Output(Reset()) // This is Output because we're going to flip it in the BlackBox
}

object SPIFlashPlusarg {
  def apply(i: Int) = s"spiflash${i}"
}

class SimSPIFlashModel(capacityBytes: BigInt, id: Int, rdOnly: Boolean = true) extends BlackBox(Map(
  "PLUSARG" -> StringParam(SPIFlashPlusarg(id)),
  "READONLY" -> IntParam(if (rdOnly) 1 else 0),
  "CAPACITY_BYTES" -> IntParam(capacityBytes))
) with HasBlackBoxResource {
  val io = IO(Flipped(new SPIFlashIO()))

  // The model adds a +spiflash<ID>=<path> plusarg. It's not a numeric, so we can't use
  // plusarg_reader, but we can still add to the PlusArgArtefacts
  // Unfortunately it requires a numeric default, but that's really just a docstring issue
  PlusArgArtefacts.append(SPIFlashPlusarg(id), 0, s"Binary image to mount to SPI flash memory ${id}")

  require(capacityBytes < 0x100000000L, "SimSPIFlashModel only supports 32-bit addressing")

  addResource("/testchipip/vsrc/SimSPIFlashModel.sv")
  addResource("/testchipip/vsrc/SPIFlashMemCtrl.sv")
  addResource("/testchipip/vsrc/plusarg_file_mem.sv")
  addResource("/testchipip/csrc/plusarg_file_mem.cc")
  addResource("/testchipip/csrc/plusarg_file_mem.h")
}

class SPIFlashMemCtrlIO(val addrBits: Int) extends Bundle {
  val sck = Input(Bool())
  val cs = Input(Bool())
  val reset = Input(Bool())
  val dq_in = Input(UInt(4.W))
  val dq_drive = Output(UInt(4.W))
  val dq_out = Output(UInt(4.W))
  val mem = new MemCtrlIO(addrBits, 8)
}

class MemCtrlIO(val addrBits: Int, val dataBits: Int) extends Bundle {
  val req = new Bundle {
    val valid = Output(Bool())
    val r_wb = Output(Bool())
    val addr = Output(UInt(addrBits.W))
    val data = Output(UInt(dataBits.W))
  }
  val resp = new Bundle {
    val data = Input(UInt(dataBits.W))
  }
}

class SPIFlashMemCtrl(addrBits: Int) extends BlackBox(Map(
  "ADDR_BITS" -> IntParam(addrBits))
) with HasBlackBoxResource {
  val io = IO(new SPIFlashMemCtrlIO(addrBits))

  addResource("/testchipip/vsrc/SPIFlashMemCtrl.sv")
}

object SimSPIFlashModel {
  def connect(spi: Seq[SPIChipIO], reset: Reset, rdOnly: Boolean = true, params: SPIFlashParams) = {
    spi.zipWithIndex.foreach { case (port, i) =>
      val spi_mem = Module(new SimSPIFlashModel(params.fSize, i, rdOnly))
      spi_mem.suggestName(s"spi_mem_${i}")
      spi_mem.io.sck := port.sck
      require(params.csWidth == 1, "I don't know what to do with your extra CS bits. Fix me please.")
      spi_mem.io.cs(0) := port.cs(0)
      spi_mem.io.dq.zip(port.dq).foreach { case (x, y) => x <> y }
      spi_mem.io.reset := reset.asBool
    }
  }
}
