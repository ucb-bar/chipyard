// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._

import org.chipsalliance.cde.config.Parameters

import freechips.rocketchip.jtag.{Chain, JtagTapGenerator, JTAGIdcodeBundle}

import firechip.bridgeinterfaces._

/** Smallest self-contained target that exercises the direct-JTAG bridge end to
  * end. The host-driven TCK/TMS/TDI/TDO pins from [[JTAGBridge]] feed a real
  * Rocket-Chip JTAG TAP whose only data register is IDCODE. With
  * version/part/mfr all zero the IDCODE reads back as 0x00000001, giving the
  * host driver a deterministic value to assert on after a TAP reset and a
  * Shift-DR of 32 bits. The TAP is clocked by the host-driven TCK, exactly as
  * in a real design.
  */
class JTAGDUT(implicit val p: Parameters) extends Module {
  val ep = Module(new JTAGBridge)
  ep.io.clock := clock
  ep.io.reset := reset.asBool

  // The enclosed TAP must be clocked from TCK (see JtagTapGenerator docs).
  val tapClock = ep.io.jtag.TCK.asClock
  withClockAndReset(tapClock, reset) {
    val idcode = WireInit(0.U.asTypeOf(new JTAGIdcodeBundle()))
    idcode.always1    := 1.U
    idcode.version    := 0.U
    idcode.partNumber := 0.U
    idcode.mfrId      := 0.U

    // No instructions other than IDCODE; every other IR value maps to BYPASS.
    // The initial instruction after Test-Logic-Reset is IDCODE, so a bare
    // Shift-DR returns the IDCODE without shifting the IR first.
    val tapIO = JtagTapGenerator(irLength = 5, instructions = Map.empty[BigInt, Chain], icode = Some(BigInt(1)))
    tapIO.idcode.get         := idcode
    tapIO.jtag.TCK           := tapClock
    tapIO.jtag.TMS           := ep.io.jtag.TMS
    tapIO.jtag.TDI           := ep.io.jtag.TDI
    ep.io.jtag.TDO           := tapIO.jtag.TDO.data
    tapIO.control.jtag_reset := reset.asAsyncReset
  }
}

class JTAGModule(implicit p: Parameters) extends firesim.lib.testutils.PeekPokeHarness(() => new JTAGDUT)
