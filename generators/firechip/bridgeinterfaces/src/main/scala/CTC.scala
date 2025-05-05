// // See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._
import chisel3.util._

// import testchipip.ctc.{CTCBridgeIO}

// class C2CTSIPortIO extends Bundle {
//   val manager = new TSIIO
//   val client = new TSIIO
// }

// class Flit(val flitWidth: Int) extends Bundle {
//   val flit = UInt(flitWidth.W)
// }

class DecoupledFlitIO(val flitWidth: Int) extends Bundle {
  // val in = Flipped(Decoupled(new Flit(flitWidth))) 
  // val out = Decoupled(new Flit(flitWidth)) 
  val in = Flipped(Decoupled(UInt(flitWidth.W)))
  val out = Decoupled(UInt(flitWidth.W))
}

class CTCBridgeIO(val w: Int) extends Bundle {
  val client_flit = new DecoupledFlitIO(w) 
  val manager_flit = new DecoupledFlitIO(w) 
}

// NOTE: CTCBridgeIO in ctc is CTC.INNER_WIDTH=32b
object CTC {
  val WIDTH = 32
}

class CTCBridgeTargetIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val ctc_io = Flipped(new CTCBridgeIO(CTC.WIDTH))
}

// // case class C2CTSIKey(otherChipId: Int)

// // Basically a pared-down SerialRAM. SerialRAM minus the RAM
// class SerialTLtoTSIAdapter(tl_serdesser: TLSerdesser, params: SerialTLParams)(implicit p: Parameters) extends LazyModule {
//   val managerParams = tl_serdesser.module.client_edge.map(_.slave) // the managerParams are the chip-side clientParams
//   val clientParams = tl_serdesser.module.manager_edge.map(_.master) // The clientParams are the chip-side managerParams
//   val serdesser = LazyModule(new TLSerdesser(
//     tl_serdesser.flitWidth,
//     clientParams,
//     managerParams,
//     tl_serdesser.bundleParams,
//     nameSuffix = Some("TSIBridgeAdapter") //hope this is chill
//   ))

//   // If if this serdesser expects a manager, connect tsi2tl
//   val tsi2tl_man = serdesser.managerNode.map { managerNode =>
//     val tsi2tl = LazyModule(new TSIToTileLink)
//     serdesser.managerNode.get := TLBuffer() := tsi2tl.node
//     tsi2tl
//   }

//   val tl2tsi_cl = serdesser.clientNode.map { clientNode =>
//     val tsi2tl = LazyModule(new TSIToTileLink)
//     serdesser.managerNode.get := TLBuffer() := tsi2tl.node
//     tsi2tl
//   }

//   lazy val module = new Impl
//   class Impl extends LazyModuleImp(this) {
//     val io = IO(new Bundle {
//       val ser = new DecoupledPhitIO(params.phyParams.phitWidth)
//       val tsi = tsi2tl.map(_ => new TSIIO)
//       val tsi2tl_state = Output(UInt())
//     })

//     val phy = Module(new DecoupledSerialPhy(5, params.phyParams))
//     phy.io.outer_clock := clock
//     phy.io.outer_reset := reset
//     phy.io.inner_clock := clock
//     phy.io.inner_reset := reset
//     phy.io.outer_ser <> io.ser
//     for (i <- 0 until 5) {
//       serdesser.module.io.ser(i) <> phy.io.inner_ser(i)
//     }
//     io.tsi.foreach(_ <> tsi2tl.get.module.io.tsi)
//     io.tsi2tl_state := tsi2tl.map(_.module.io.state).getOrElse(0.U(1.W))

//     require(serdesser.module.mergedParams == tl_serdesser.module.mergedParams,
//     "Mismatch between chip-side diplomatic params and harness-side diplomatic params:\n" +
//       s"Harness-side params: ${serdesser.module.mergedParams}\n" +
//       s"Chip-side params: ${tl_serdesser.module.mergedParams}")

//   }
// }