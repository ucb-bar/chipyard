// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class PCIePacket extends Bundle {
  val client_data = Vec(7, UInt(CTC.WIDTH.W))
  val client_valid = Vec(7, Bool())
  val client_ready = Vec(7, Bool())
  val manager_data = Vec(7, UInt(CTC.WIDTH.W))
  val manager_valid = Vec(7, Bool())
  val manager_ready = Vec(7, Bool())
  val pad = UInt(36.W)
}

class CTCToken extends Bundle {
  val client_data = UInt(CTC.WIDTH.W)
  val client_valid = Bool()
  val client_ready = Bool()
  val manager_data = UInt(CTC.WIDTH.W)
  val manager_valid = Bool()
  val manager_ready = Bool()
}

class CTCToHostAdapter extends Module {
  val io = IO(new Bundle {
    val ctc_in = Flipped(Decoupled(new CTCToken))
    val pcie_out = Decoupled(UInt(512.W))
  })

  val ctcTokenBuf = Reg(Vec(7, new CTCToken))
  val counter = RegInit(0.U(3.W))

  when (io.ctc_in.valid) {
    ctcTokenBuf(counter) := io.ctc_in.bits
  }

  io.ctc_in.ready := (counter === 6.U && io.pcie_out.ready) || (counter =/= 6.U)
  io.pcie_out.valid := counter === 6.U && io.ctc_in.valid
  when (counter =/= 6.U && io.ctc_in.valid) {
    counter := counter + 1.U
  } .elsewhen ((counter === 6.U) && io.ctc_in.valid && io.pcie_out.ready) { // pcie_out.fire()
    counter := 0.U
  } .otherwise {
    counter := counter
  }

  val out = Wire(new PCIePacket)
  for (i <- 0 until 6) {
    out.client_data(i) := ctcTokenBuf(i).client_data
    out.client_valid(i) := ctcTokenBuf(i).client_valid
    out.client_ready(i) := ctcTokenBuf(i).client_ready
    out.manager_data(i) := ctcTokenBuf(i).manager_data
    out.manager_valid(i) := ctcTokenBuf(i).manager_valid
    out.manager_ready(i) := ctcTokenBuf(i).manager_ready
  }
  out.client_data(6) := io.ctc_in.bits.client_data
  out.client_valid(6) := io.ctc_in.bits.client_valid
  out.client_ready(6) := io.ctc_in.bits.client_ready
  out.manager_data(6) := io.ctc_in.bits.manager_data
  out.manager_valid(6) := io.ctc_in.bits.manager_valid
  out.manager_ready(6) := io.ctc_in.bits.manager_ready
  out.pad := 0.U

  io.pcie_out.bits := out.asUInt
}

class HostToCTCAdapter extends Module {
  val io = IO(new Bundle {
    val pcie_in = Flipped(Decoupled(UInt(512.W)))
    val ctc_out = Decoupled(new CTCToken)
  })

  val packet_in = io.pcie_in.bits.asTypeOf(new PCIePacket)
  val counter = RegInit(0.U(3.W))

  when (io.ctc_out.fire()) {
    counter := Mux(counter === 6.U, 0.U, counter + 1.U)
  }

  io.ctc_out.valid := io.pcie_in.valid
  io.pcie_in.ready := io.ctc_out.ready && counter === 6.U

  io.ctc_out.bits.client_data := packet_in.client_data(counter)
  io.ctc_out.bits.client_valid := packet_in.client_valid(counter)
  io.ctc_out.bits.client_ready := packet_in.client_ready(counter)
  io.ctc_out.bits.manager_data := packet_in.manager_data(counter)
  io.ctc_out.bits.manager_valid := packet_in.manager_valid(counter)
  io.ctc_out.bits.manager_ready := packet_in.manager_ready(counter)
}

class CTCBridgeModule(implicit p: Parameters) extends BridgeModule[HostPortIO[CTCBridgeTargetIO]]()(p) 
    with StreamToHostCPU
    with StreamFromHostCPU {
  // Stream mixin parameters, stealing value from NIC
  val fromHostCPUQueueDepth = 3072
  val toHostCPUQueueDepth   = 3072
  
  lazy val module = new BridgeModuleImp(this) {
    // Copied from TSIBridge thank youuuu tsi bridge
    val io = IO(new WidgetIO)
    val hPort = IO(HostPort(new CTCBridgeTargetIO))

    val inBuf  = Module(new Queue(new CTCToken, 10))
    val outBuf = Module(new Queue(new CTCToken, 10))
    // val clientInBuf  = Module(new Queue(new CTCToken, 10))
    // val clientOutBuf = Module(new Queue(new CTCToken, 10))
    // val managerInBuf  = Module(new Queue(new CTCToken, 10))
    // val managerOutBuf = Module(new Queue(new CTCToken, 10))

    val target = hPort.hBits.ctc_io
    val tFire = hPort.toHost.hValid && hPort.fromHost.hReady //&& outBuf.io.enq.ready && inBuf.io.deq.valid // my buffers can receive a token and I can provide a token
    val targetReset = tFire & hPort.hBits.reset

    hPort.toHost.hReady := outBuf.io.enq.ready
    hPort.fromHost.hValid := inBuf.io.deq.valid

    // Connect CTC to Host buffers
    outBuf.io.enq.bits.client_data := target.client_flit.out.bits
    outBuf.io.enq.bits.client_valid := target.client_flit.out.valid
    outBuf.io.enq.bits.client_ready := target.client_flit.in.ready
    //target.client_flit.out.ready := tFire // I think EDIT: NO!!!!!

    outBuf.io.enq.bits.manager_data := target.manager_flit.out.bits
    outBuf.io.enq.bits.manager_valid := target.manager_flit.out.valid
    outBuf.io.enq.bits.manager_ready := target.manager_flit.in.ready
    //target.manager_flit.out.ready := tFire // I think

    outBuf.io.enq.valid := hPort.toHost.hValid

    // Convert to wider host token + send over pcie
    val ctc2host = Module(new CTCToHostAdapter)
    ctc2host.io.ctc_in <> outBuf.io.deq
    streamEnq <> ctc2host.io.pcie_out

    // Receive from pcie and convert back to CTC tokens
    val host2ctc = Module(new HostToCTCAdapter)
    host2ctc.io.pcie_in <> streamDeq
    inBuf.io.enq <> host2ctc.io.ctc_out

    // Connect host to CTC buffers
    // Incoming manager bits drive my client, incoming client bits drive my manager
    target.client_flit.in.valid := inBuf.io.deq.bits.manager_valid
    target.client_flit.in.bits := inBuf.io.deq.bits.manager_data
    target.client_flit.out.ready := inBuf.io.deq.bits.manager_ready

    target.manager_flit.in.valid := inBuf.io.deq.bits.client_valid
    target.manager_flit.in.bits := inBuf.io.deq.bits.client_data
    target.manager_flit.out.ready := inBuf.io.deq.bits.client_ready

    inBuf.io.deq.ready := hPort.fromHost.hReady

    genROReg(!tFire, "done") // dummy MMIO reg

    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(
          base, sb, 
          "ctc_t", 
          "ctc",           
          Seq(
            UInt32(toHostStreamIdx),
            UInt32(toHostCPUQueueDepth),
            UInt32(fromHostStreamIdx),
            UInt32(fromHostCPUQueueDepth),
          ),
          hasStreams = true)
    }
  }
}