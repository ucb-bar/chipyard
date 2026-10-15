// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

// Host-side Golden Gate implementation of the direct JTAG bridge.
//
// The host controls the target's JTAG pins over MMIO:
//   - tck/tms/tdi are write-only registers driven from the host driver
//   - tdo is a read-only register that captures the target's TDO whenever a
//     target token is exchanged (`fire`)
// Each bridge tick exchanges one target token and corresponds to one host
// remote_bitbang operation (a pin update or a TDO sample), so the host driver
// fully owns JTAG timing. A token is a single pin/TDO operation, not a complete
// JTAG bit; a full bit needs at least a TCK-low then TCK-high pin update, with a
// separate TDO read when the client requests one.
class JTAGBridgeModule(bridgeParams: JTAGBridgeParams)(implicit p: Parameters)
    extends BridgeModule[HostPortIO[JTAGBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO)
    val hPort = IO(HostPort(new JTAGBridgeTargetIO))

    val target = hPort.hBits.jtag

    val tckReg = RegInit(false.B)
    val tmsReg = RegInit(true.B)
    val tdiReg = RegInit(true.B)
    val tdoReg = Reg(Bool())

    val fire = hPort.toHost.hValid && hPort.fromHost.hReady

    hPort.toHost.hReady := fire
    hPort.fromHost.hValid := fire

    when(fire) {
      tdoReg := target.TDO
    }

    target.TCK := tckReg
    target.TMS := tmsReg
    target.TDI := tdiReg

    genWOReg(tckReg, "tck")
    genWOReg(tmsReg, "tms")
    genWOReg(tdiReg, "tdi")
    genROReg(tdoReg, "tdo")

    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(base, sb, "jtagbridge_t", "jtagbridge")
    }
  }
}
