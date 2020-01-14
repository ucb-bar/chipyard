package utilities

import chisel3._
import chisel3.util._
import chisel3.experimental.IntParam
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util.HeterogeneousBag

class SimDRAMModel(params: AXI4BundleParameters, size: BigInt)(implicit p: Parameters) extends BlackBox(Map(
    "MEM_SIZE" -> IntParam(size),
    "LINE_SIZE" -> IntParam(p(CacheBlockBytes)),
    "ADDR_BITS" -> IntParam(params.addrBits),
    "DATA_BITS" -> IntParam(params.dataBits),
    "ID_BITS" -> IntParam(params.idBits)))
  with HasBlackBoxResource {

  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val axi = Flipped(new AXI4Bundle(params))
  })

  addResource("/vsrc/SimDRAM.v")
  addResource("/csrc/SimDRAM.cc")
  addResource("/csrc/dramsim/mm.cc")
  addResource("/csrc/dramsim/mm.h")
  addResource("/csrc/dramsim/mm_dramsim2.cc")
  addResource("/csrc/dramsim/mm_dramsim2.h")
}

class SimDRAM(edge: AXI4EdgeParameters, size: BigInt)(implicit p: Parameters) extends LazyModule {
  val masterNode = AXI4MasterNode(Seq(edge.master))
  val slaveNode = AXI4SlaveNode(Seq(edge.slave.copy(
    slaves = edge.slave.slaves.map { s =>
      s.copy(address = Seq(AddressSet(0, size-1)))
    })))

  slaveNode := masterNode

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val axi4 = Flipped(HeterogeneousBag.fromNode(masterNode.out))
    })

    (masterNode.out zip io.axi4) foreach { case ((out, _), io) => out <> io }

    slaveNode.in foreach { case (in, _) =>
      val sim = Module(new SimDRAMModel(in.params, size))
      sim.io.axi <> in
      sim.io.clock := clock
      sim.io.reset := reset
    }
  }
}

trait HasDRAMModuleImp extends LazyModuleImp {
  this: CanHaveMasterAXI4MemPortModuleImp =>

  def connectSimDRAM() {
    (mem_axi4 zip outer.memAXI4Node).foreach { case (io, node) =>
      (io zip node.in).foreach { case (io, (_, edge)) =>
        val mem = LazyModule(new SimDRAM(edge, p(ExtMem).get.master.size))
        Module(mem.module).io.axi4.head <> io
      }
    }
  }
}
