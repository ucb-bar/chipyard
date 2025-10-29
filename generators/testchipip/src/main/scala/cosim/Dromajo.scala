package testchipip.cosim

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam}
import freechips.rocketchip.subsystem.{InSubsystem}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.util.{UIntToAugmentedUInt, ElaborationArtefacts}
import freechips.rocketchip.tile.{TraceBundle}
import freechips.rocketchip.subsystem.{ExtMem, HierarchicalLocation}
import freechips.rocketchip.devices.tilelink.{BootROMLocated, CLINTConsts, CLINTKey, PLICConsts, PLICKey}

object DromajoConstants {
  val xLen = 64
  val instBits = 32
  val maxHartIdBits = 32
}

/**
 * Helper object/function to generate Dromajo header file
 */
object DromajoHelper {
  def addArtefacts(location: HierarchicalLocation)(implicit p: Parameters): Unit = {
    var dromajoParams: String = ""
    dromajoParams += "#ifndef DROMAJO_PARAMS_H"
    dromajoParams += "\n#define DROMAJO_PARAMS_H"
    p(BootROMLocated(location)) map { brP =>
      dromajoParams += "\n\n" + "#define DROMAJO_RESET_VECTOR " + "\"" + "0x" + f"${brP.hang}%X" + "\""
      dromajoParams += "\n" + "#define DROMAJO_MMIO_START " + "\"" + "0x" + f"${brP.address + brP.size}%X" + "\""
    }
    p(ExtMem) map { eP =>
      dromajoParams += "\n" + "#define DROMAJO_MMIO_END " + "\"" + "0x" + f"${eP.master.base}%X" + "\""
      // dromajo memory is in MiB chunks
      dromajoParams += "\n" + "#define DROMAJO_MEM_SIZE " + "\"" + "0x" + f"${eP.master.size >> 20}%X" + "\""
    }
    p(PLICKey) map { pP =>
      dromajoParams += "\n" + "#define DROMAJO_PLIC_BASE " + "\"" + "0x" + f"${pP.baseAddress}%X" + "\""
      dromajoParams += "\n" + "#define DROMAJO_PLIC_SIZE " + "\"" + "0x" + f"${PLICConsts.size(pP.maxHarts)}%X" + "\""
    }
    p(CLINTKey) map { cP =>
      dromajoParams += "\n" + "#define DROMAJO_CLINT_BASE " + "\"" + "0x" + f"${cP.baseAddress}%X" + "\""
      dromajoParams += "\n" + "#define DROMAJO_CLINT_SIZE " + "\"" + "0x" + f"${CLINTConsts.size}%X" + "\""
    }
    dromajoParams += "\n\n#endif"

    ElaborationArtefacts.add("""dromajo_params.h""", dromajoParams)
  }
}

/**
 * Dromajo bridge to input instruction streams and check with Dromajo
 */
class SimDromajoBridge(traceType: TraceBundle) extends Module
{
  val io = IO(new Bundle {
    val trace = Input(new TileTraceIO(traceType))
  })
  val numInsns = io.trace.trace.insns.size

  val traces = io.trace.trace.insns

  val dromajo = Module(new SimDromajoCosimBlackBox(numInsns))

  dromajo.io.clock := clock
  dromajo.io.reset := reset.asBool

  dromajo.io.valid := Cat(traces.map(t => t.valid).reverse)
  dromajo.io.hartid := 0.U
  dromajo.io.pc := Cat(traces.map(t => UIntToAugmentedUInt(t.iaddr).sextTo(DromajoConstants.xLen)).reverse)
  dromajo.io.inst := Cat(traces.map(t => t.insn.pad(DromajoConstants.instBits)).reverse)
  dromajo.io.wdata := Cat(traces.map(t => UIntToAugmentedUInt(t.wdata.get).sextTo(DromajoConstants.xLen)).reverse)
  dromajo.io.mstatus := 0.U // dromajo doesn't use mstatus currently
  dromajo.io.check := ((1 << traces.size) - 1).U

  // assumes that all interrupt/exception signals are the same throughout all committed instructions
  dromajo.io.int_xcpt := traces(0).interrupt || traces(0).exception
  dromajo.io.cause := traces(0).cause.pad(DromajoConstants.xLen) | (traces(0).interrupt << DromajoConstants.xLen-1)
}

/**
 * Helper function to connect Dromajo bridge.
 * Mirrors the Dromajo bridge in FireSim.
 */
object SimDromajoBridge
{
  def apply(traceIO: TileTraceIO)(implicit p: Parameters): SimDromajoBridge = {
    val dbridge = Module(new SimDromajoBridge(traceIO.traceType))

    dbridge.io.trace := traceIO

    dbridge
  }
}

/**
 * Connect to the Dromajo Cosimulation Tool through a BB
 */
class SimDromajoCosimBlackBox(commitWidth: Int)
  extends BlackBox(Map(
    "COMMIT_WIDTH" -> IntParam(commitWidth),
    "XLEN" -> IntParam(DromajoConstants.xLen),
    "INST_BITS" -> IntParam(DromajoConstants.instBits),
    "HARTID_LEN" -> IntParam(DromajoConstants.maxHartIdBits)
  ))
  with HasBlackBoxResource
{
  val instBits = DromajoConstants.instBits
  val maxHartIdBits = DromajoConstants.maxHartIdBits
  val xLen = DromajoConstants.xLen

  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())

    val valid   = Input(UInt(         (commitWidth).W))
    val hartid  = Input(UInt(       (maxHartIdBits).W))
    val pc      = Input(UInt(    (xLen*commitWidth).W))
    val inst    = Input(UInt((instBits*commitWidth).W))
    val wdata   = Input(UInt(    (xLen*commitWidth).W))
    val mstatus = Input(UInt(    (xLen*commitWidth).W))
    val check   = Input(UInt(         (commitWidth).W))

    val int_xcpt = Input(      Bool())
    val cause    = Input(UInt(xLen.W))
  })

  addResource("/testchipip/vsrc/SimDromajoCosimBlackBox.v")
  addResource("/testchipip/csrc/SimDromajoCosim.cc")
  addResource("/testchipip/csrc/dromajo_wrapper.cc")
  addResource("/testchipip/csrc/dromajo_wrapper.h")
}
