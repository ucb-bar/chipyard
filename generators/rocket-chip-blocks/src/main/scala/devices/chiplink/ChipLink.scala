package sifive.blocks.devices.chiplink

import chisel3._ 
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._

class ChipLink(val params: ChipLinkParams)(implicit p: Parameters) extends LazyModule() {

  val device = new SimpleBus("chiplink", Seq("sifive,chiplink"))

  private def maybeManager(x: Seq[AddressSet], f: Seq[AddressSet] => TLSlaveParameters) =
    if (x.isEmpty) Nil else Seq(f(x))

  private val slaveNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers =
      maybeManager(params.TLUH, a => TLSlaveParameters.v1(
        address            = a,
        resources          = device.ranges,
        regionType         = RegionType.GET_EFFECTS,
        executable         = true,
        supportsArithmetic = params.atomicXfer,
        supportsLogical    = params.atomicXfer,
        supportsGet        = params.fullXfer,
        supportsPutFull    = params.fullXfer,
        supportsPutPartial = params.fullXfer,
        supportsHint       = params.fullXfer,
        mayDenyPut         = true,
        mayDenyGet         = true,
        fifoId             = Some(0))) ++
      maybeManager(params.TLC, a => TLSlaveParameters.v1(
        address            = a,
        resources          = device.ranges,
        regionType         = RegionType.TRACKED,
        executable         = true,
        supportsAcquireT   = params.acqXfer,
        supportsAcquireB   = params.acqXfer,
        supportsArithmetic = params.atomicXfer,
        supportsLogical    = params.atomicXfer,
        supportsGet        = params.fullXfer,
        supportsPutFull    = params.fullXfer,
        supportsPutPartial = params.fullXfer,
        supportsHint       = params.fullXfer,
        mayDenyPut         = true,
        mayDenyGet         = true,
        fifoId             = Some(0))),
    beatBytes  = 4,
    endSinkId  = params.sinks,
    minLatency = params.latency)))

  // Masters 1+ require order; Master 0 is unordered and may cache
  private val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq.tabulate(params.domains) { i =>
      TLMasterParameters.v1(
        name          = "ChipLink Domain #" + i,
        sourceId      = IdRange(i*params.sourcesPerDomain, (i + 1)*params.sourcesPerDomain),
        requestFifo   = i > 0,
        supportsProbe = if (i == 0) params.fullXfer else params.noXfer) },
    minLatency = params.latency)))

  private val sbypass = LazyModule(new TLBusBypass(beatBytes = 4))
  slaveNode := sbypass.node

  private val mute = LazyModule(new MuteMaster(maxProbe = params.acqXfer.max))
  private val mbypass = LazyModule(new StuckSnooper(_.last))
  mbypass.node := mute.node
  mbypass.node := masterNode

  val node = NodeHandle(sbypass.node, mbypass.node)
  val ioNode = BundleBridgeSource(() => new WideDataLayerPort(params).cloneType)

  // Exported memory map. Used when connecting VIP
  lazy val managers = masterNode.edges.out(0).manager.managers
  lazy val mmap = {
    val (tlc, tluh) = managers.partition(_.supportsAcquireB)
    params.copy(
      TLUH = AddressSet.unify(tluh.flatMap(_.address)),
      TLC  = AddressSet.unify(tlc.flatMap(_.address)))
  }

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val bypass = Output(Bool())
      // When not syncTX, these drive the TX domain
      val c2b_clk = Input(Clock())
      val c2b_rst = Input(Bool())
      // If fpgaReset, we need a pulse that arrives before b2c_clk locks
      val fpga_reset = if (params.fpgaReset) Some(Input(Bool())) else None
    })
    val port = ioNode.bundle

    // Ensure downstream devices support our requirements
    val (in,  edgeIn)  = slaveNode.in(0)
    val (out, edgeOut) = masterNode.out(0)

    require (edgeIn.manager.beatBytes == 4)
    edgeOut.manager.requireFifo()

    edgeOut.manager.managers.foreach { m =>
      require (m.supportsGet.contains(params.fullXfer),
        s"ChipLink requires ${m.name} support ${params.fullXfer} Get, not ${m.supportsGet}")
      if (m.supportsPutFull) {
        require (m.supportsPutFull.contains(params.fullXfer),
          s"ChipLink requires ${m.name} support ${params.fullXfer} PutFill, not ${m.supportsPutFull}")
        // !!! argh. AHB devices can't: require (m.supportsPutPartial.contains(params.fullXfer),
        //  s"ChipLink requires ${m.name} support ${params.fullXfer} PutPartial not ${m.supportsPutPartial}")
        require (m.supportsArithmetic.contains(params.atomicXfer),
          s"ChipLink requires ${m.name} support ${params.atomicXfer} Arithmetic, not ${m.supportsArithmetic}")
        require (m.supportsLogical.contains(params.atomicXfer),
          s"ChipLink requires ${m.name} support ${params.atomicXfer} Logical, not ${m.supportsLogical}")
      }
      require (m.supportsHint.contains(params.fullXfer),
        s"ChipLink requires ${m.name} support ${params.fullXfer} Hint, not ${m.supportsHint}")
      require (!m.supportsAcquireT || m.supportsAcquireT.contains(params.acqXfer),
        s"ChipLink requires ${m.name} support ${params.acqXfer} AcquireT, not ${m.supportsAcquireT}")
      require (!m.supportsAcquireB || m.supportsAcquireB.contains(params.acqXfer),
        s"ChipLink requires ${m.name} support ${params.acqXfer} AcquireB, not ${m.supportsAcquireB}")
      require (!m.supportsAcquireB || !m.supportsPutFull || m.supportsAcquireT,
        s"ChipLink requires ${m.name} to support AcquireT if it supports Put and AcquireB")
    }

    // Anything that is optional, must be supported by the error device (for redirect)
    val errorDevs = edgeOut.manager.managers.filter(_.nodePath.last.lazyModule.className == "TLError")
    require (errorDevs.exists(_.supportsAcquireB), "There is no TLError with Acquire reachable from ChipLink. One must be instantiated.")
    val errorDev = errorDevs.find(_.supportsAcquireB).get
    require (errorDev.supportsPutFull.contains(params.fullXfer),
      s"ChipLink requires ${errorDev.name} support ${params.fullXfer} PutFill, not ${errorDev.supportsPutFull}")
    require (errorDev.supportsPutPartial.contains(params.fullXfer),
      s"ChipLink requires ${errorDev.name} support ${params.fullXfer} PutPartial not ${errorDev.supportsPutPartial}")
    require (errorDev.supportsArithmetic.contains(params.atomicXfer),
      s"ChipLink requires ${errorDev.name} support ${params.atomicXfer} Arithmetic, not ${errorDev.supportsArithmetic}")
    require (errorDev.supportsLogical.contains(params.atomicXfer),
      s"ChipLink requires ${errorDev.name} support ${params.atomicXfer} Logical, not ${errorDev.supportsLogical}")
    require (errorDev.supportsAcquireT.contains(params.acqXfer),
      s"ChipLink requires ${errorDev.name} support ${params.acqXfer} AcquireT, not ${errorDev.supportsAcquireT}")

    // At most one cache can master ChipLink
    require (edgeIn.client.clients.filter(_.supports.probe).size <= 1,
      s"ChipLink supports at most one caching master, ${edgeIn.client.clients.filter(_.supports.probe).map(_.name)}")

    // Construct the info needed by all submodules
    val info = ChipLinkInfo(params, edgeIn, edgeOut, errorDev.address.head)

    val sinkA = Module(new SinkA(info))
    val sinkB = Module(new SinkB(info))
    val sinkC = Module(new SinkC(info))
    val sinkD = Module(new SinkD(info))
    val sinkE = Module(new SinkE(info))
    val sourceA = Module(new SourceA(info))
    val sourceB = Module(new SourceB(info))
    val sourceC = Module(new SourceC(info))
    val sourceD = Module(new SourceD(info))
    val sourceE = Module(new SourceE(info))

    val rx = Module(new RX(info))
    rx.clock := port.b2c.clk

    // The off-chip reset is registered internally to improve timing
    // The RX module buffers incoming data by one cycle to compensate the reset delay
    // It is required that the internal reset be high even when the b2c.clk does not run
    io.fpga_reset match {
      case None =>
        // b2c.rst is actually synchronous to b2c.clk, so one flop is enough
        rx.reset := AsyncResetReg(false.B, port.b2c.clk, port.b2c.rst, true, None)
      case Some(resetPulse) =>
        // For high performance, FPGA IO buffer registers must feed IO into D, not reset
        // However, FPGA registers also support an initial block to generate a reset pulse
        rx.reset := AsyncResetReg(port.b2c.rst, port.b2c.clk, resetPulse, true, None)
    }

    rx.io.b2c_data := port.b2c.data
    rx.io.b2c_send := port.b2c.send
    out.a <> sourceA.io.a
    in .b <> sourceB.io.b
    out.c <> sourceC.io.c
    in .d <> sourceD.io.d
    out.e <> sourceE.io.e
    sourceA.io.q <> FromAsyncBundle(rx.io.a)
    sourceB.io.q <> FromAsyncBundle(rx.io.b)
    sourceC.io.q <> FromAsyncBundle(rx.io.c)
    sourceD.io.q <> FromAsyncBundle(rx.io.d)
    sourceE.io.q <> FromAsyncBundle(rx.io.e)

    val tx = Module(new TX(info))
    port.c2b.clk := tx.io.c2b_clk
    port.c2b.rst := tx.io.c2b_rst
    port.c2b.data := tx.io.c2b_data
    port.c2b.send := tx.io.c2b_send
    sinkA.io.a <> in .a
    sinkB.io.b <> out.b
    sinkC.io.c <> in .c
    sinkD.io.d <> out.d
    sinkE.io.e <> in .e
    if (params.syncTX) {
      tx.io.sa <> sinkA.io.q
      tx.io.sb <> sinkB.io.q
      tx.io.sc <> sinkC.io.q
      tx.io.sd <> sinkD.io.q
      tx.io.se <> sinkE.io.q
    } else {
      // Create the TX clock domain from input
      tx.clock := io.c2b_clk
      tx.reset := io.c2b_rst
      tx.io.a <> ToAsyncBundle(sinkA.io.q, params.crossing)
      tx.io.b <> ToAsyncBundle(sinkB.io.q, params.crossing)
      tx.io.c <> ToAsyncBundle(sinkC.io.q, params.crossing)
      tx.io.d <> ToAsyncBundle(sinkD.io.q, params.crossing)
      tx.io.e <> ToAsyncBundle(sinkE.io.q, params.crossing)
    }

    // Pass credits from RX to TX
    tx.io.rxc <> rx.io.rxc
    tx.io.txc <> rx.io.txc

    // Connect the CAM source pools
    sinkD.io.a_clSource := sourceA.io.d_clSource
    sourceA.io.d_tlSource := sinkD.io.a_tlSource
    sinkD.io.c_clSource := sourceC.io.d_clSource
    sourceC.io.d_tlSource := sinkD.io.c_tlSource
    sourceD.io.e_tlSink := sinkE.io.d_tlSink
    sinkE.io.d_clSink := sourceD.io.e_clSink

    // Disable ChipLink while RX+TX are in reset
    val do_bypass = ResetCatchAndSync(clock, rx.reset.asBool) || ResetCatchAndSync(clock, tx.reset.asBool)
    sbypass.module.io.bypass := do_bypass
    mbypass.module.io.bypass := do_bypass
    io.bypass := do_bypass
  }
}

/*
   Copyright 2016 SiFive, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
