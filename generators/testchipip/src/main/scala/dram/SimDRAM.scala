package testchipip.dram

import chisel3._
import chisel3.experimental.IntParam
import chisel3.util.HasBlackBoxResource
import freechips.rocketchip.amba.axi4.{AXI4BundleParameters, AXI4Bundle}

class SimDRAM(memSize: BigInt, lineSize: Int, clockFreqHz: BigInt, memBase: BigInt,
              params: AXI4BundleParameters, chipId: Int) extends BlackBox(Map(
                "MEM_SIZE" -> IntParam(memSize),
                "LINE_SIZE" -> IntParam(lineSize),
                "ADDR_BITS" -> IntParam(params.addrBits),
                "DATA_BITS" -> IntParam(params.dataBits),
                "ID_BITS" -> IntParam(params.idBits),
                "CLOCK_HZ" -> IntParam(clockFreqHz),
                "MEM_BASE" -> IntParam(memBase),
                "CHIP_ID" -> IntParam(chipId)
              )) with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val axi = Flipped(new AXI4Bundle(params))
  })

  addResource("/testchipip/vsrc/SimDRAM.v")
  addResource("/testchipip/csrc/SimDRAM.cc")
  addResource("/testchipip/csrc/mm.cc")
  addResource("/testchipip/csrc/mm.h")
  addResource("/testchipip/csrc/mm_dramsim2.cc")
  addResource("/testchipip/csrc/mm_dramsim2.h")
}
