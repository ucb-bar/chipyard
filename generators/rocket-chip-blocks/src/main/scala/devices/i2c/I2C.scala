/////////////////////////////////////////////////////////////////////
////                                                             ////
////  WISHBONE revB.2 compliant I2C Master controller Top-level  ////
////                                                             ////
////                                                             ////
////  Author: Richard Herveille                                  ////
////          richard@asics.ws                                   ////
////          www.asics.ws                                       ////
////                                                             ////
////  Downloaded from: http://www.opencores.org/projects/i2c/    ////
////                                                             ////
/////////////////////////////////////////////////////////////////////
////                                                             ////
//// Copyright (C) 2001 Richard Herveille                        ////
////                    richard@asics.ws                         ////
////                                                             ////
//// This source file may be used and distributed without        ////
//// restriction provided that this copyright statement is not   ////
//// removed from the file and that any derivative work contains ////
//// the original copyright notice and the associated disclaimer.////
////                                                             ////
////     THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY     ////
//// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED   ////
//// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS   ////
//// FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL THE AUTHOR      ////
//// OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,         ////
//// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES    ////
//// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE   ////
//// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR        ////
//// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF  ////
//// LIABILITY, WHETHER IN  CONTRACT, STRICT LIABILITY, OR TORT  ////
//// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  ////
//// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE         ////
//// POSSIBILITY OF SUCH DAMAGE.                                 ////
////                                                             ////
/////////////////////////////////////////////////////////////////////

// This code was re-written in Chisel by SiFive, Inc.
// See LICENSE for license details.
// WISHBONE interface replaced by Tilelink2

package sifive.blocks.devices.i2c

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.{AsyncResetRegVec, Majority}

import sifive.blocks.util._

case class I2CParams(
  address: BigInt,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceParams

class I2CPin extends Bundle {
  val in  = Input(Bool())
  val out = Output(Bool())
  val oe  = Output(Bool())
}

class I2CPort extends Bundle {
  val scl = new I2CPin
  val sda = new I2CPin
}

abstract class I2C(busWidthBytes: Int, params: I2CParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "i2c",
        compat = Seq("sifive,i2c0"),
        base = params.address,
        beatBytes = busWidthBytes),
      new I2CPort)
    with HasInterruptSources {

  def nInterrupts = 1

  lazy val module = new LazyModuleImp(this) {

  val I2C_CMD_NOP   = 0x00.U
  val I2C_CMD_START = 0x01.U
  val I2C_CMD_STOP  = 0x02.U
  val I2C_CMD_WRITE = 0x04.U
  val I2C_CMD_READ  = 0x08.U

  class PrescalerBundle extends Bundle{
    val hi = UInt(8.W)
    val lo = UInt(8.W)
  }

  class ControlBundle extends Bundle{
    val coreEn             = Bool()
    val intEn              = Bool()
    val reserved           = UInt(6.W)
  }

  class CommandBundle extends Bundle{
    val start              = Bool()
    val stop               = Bool()
    val read               = Bool()
    val write              = Bool()
    val ack                = Bool()
    val reserved           = UInt(2.W)
    val irqAck             = Bool()
  }

  class StatusBundle extends Bundle{
    val receivedAck        = Bool()    // received aknowledge from slave
    val busy               = Bool()
    val arbLost            = Bool()
    val reserved           = UInt(3.W)
    val transferInProgress = Bool()
    val irqFlag            = Bool()
  }

  // control state visible to SW/driver
  val prescaler    = RegInit(0xFFFF.U.asTypeOf(new PrescalerBundle())) 
  val control      = RegInit(0.U.asTypeOf(new ControlBundle())) 
  val transmitData = RegInit(0.U(8.W))
  val receivedData = RegInit(0.U(8.W))
  val cmd          = RegInit(0.U.asTypeOf(new CommandBundle())) 
  val status       = RegInit(0.U.asTypeOf(new StatusBundle())) 


  //////// Bit level ////////

  port.scl.out := false.B                           // i2c clock line output
  port.sda.out := false.B                           // i2c data line output

  // filter SCL and SDA signals; (attempt to) remove glitches
  val filterCnt = RegInit(0.U(14.W))
  when ( !control.coreEn ) {
    filterCnt := 0.U
  } .elsewhen (!(filterCnt.orR)) {
    filterCnt := Cat(prescaler.hi, prescaler.lo) >> 2  //16x I2C bus frequency
  } .otherwise {
    filterCnt := filterCnt - 1.U
  }

  val fSCL      = RegInit(0x7.U(3.W))
  val fSDA      = RegInit(0x7.U(3.W))
  when (!(filterCnt.orR)) {
    fSCL := Cat(fSCL, port.scl.in)
    fSDA := Cat(fSDA, port.sda.in)
  }

  val sSCL      = RegNext(Majority(fSCL), true.B)
  val sSDA      = RegNext(Majority(fSDA), true.B)

  val dSCL      = RegNext(sSCL, true.B)
  val dSDA      = RegNext(sSDA, true.B)

  val dSCLOen   = RegNext(port.scl.oe) // delayed scl_oen

  // detect start condition => detect falling edge on SDA while SCL is high
  // detect stop  condition => detect rising  edge on SDA while SCL is high
  val startCond = RegNext(!sSDA &&  dSDA && sSCL, false.B)
  val stopCond  = RegNext(sSDA && !dSDA && sSCL, false.B)

  // master drives SCL high, but another master pulls it low
  // master start counting down its low cycle now (clock synchronization)
  val sclSync   = dSCL && !sSCL && port.scl.oe

  // slave_wait is asserted when master wants to drive SCL high, but the slave pulls it low
  // slave_wait remains asserted until the slave releases SCL
  val slaveWait = RegInit(false.B)
  slaveWait := (port.scl.oe && !dSCLOen && !sSCL) || (slaveWait && !sSCL)

  val clkEn     = RegInit(true.B)     // clock generation signals
  val cnt       = RegInit(0.U(16.W))  // clock divider counter (synthesis)

  // generate clk enable signal
  when (!(cnt.orR) || !control.coreEn || sclSync ) {
    cnt   := Cat(prescaler.hi, prescaler.lo)
    clkEn := true.B
  }
  .elsewhen (slaveWait) {
    clkEn := false.B
  }
  .otherwise {
    cnt   := cnt - 1.U
    clkEn := false.B
  }

  val sclOen     = RegInit(true.B)
  port.scl.oe := !sclOen

  val sdaOen     = RegInit(true.B)
  port.sda.oe := !sdaOen

  val sdaChk     = RegInit(false.B)       // check SDA output (Multi-master arbitration)

  val transmitBit = RegInit(false.B)
  val receivedBit = Reg(Bool())
  when (sSCL && !dSCL) {
    receivedBit := sSDA
  }

  val bitCmd      = RegInit(0.U(4.W)) // command (from byte controller)
  val bitCmdStop  = RegInit(false.B)
  when (clkEn) {
    bitCmdStop := bitCmd === I2C_CMD_STOP
  }
  val bitCmdAck   = RegInit(false.B)

  val (s_bit_idle ::
       s_bit_start_a :: s_bit_start_b :: s_bit_start_c :: s_bit_start_d :: s_bit_start_e ::
       s_bit_stop_a  :: s_bit_stop_b  :: s_bit_stop_c  :: s_bit_stop_d  ::
       s_bit_rd_a    :: s_bit_rd_b    :: s_bit_rd_c    :: s_bit_rd_d    ::
       s_bit_wr_a    :: s_bit_wr_b    :: s_bit_wr_c    :: s_bit_wr_d    :: Nil) = Enum(18)
  val bitState    = RegInit(s_bit_idle)

  val arbLost     = RegNext((sdaChk && !sSDA && sdaOen) | ((bitState =/= s_bit_idle) && stopCond && !bitCmdStop), false.B)

  // bit FSM
  when (arbLost) {
    bitState  := s_bit_idle
    bitCmdAck := false.B
    sclOen    := true.B
    sdaOen    := true.B
    sdaChk    := false.B
  }
  .otherwise {
    bitCmdAck := false.B

    when (clkEn) {
      switch (bitState) {
        is (s_bit_idle) {
          switch (bitCmd) {
            is (I2C_CMD_START) { bitState := s_bit_start_a }
            is (I2C_CMD_STOP)  { bitState := s_bit_stop_a  }
            is (I2C_CMD_WRITE) { bitState := s_bit_wr_a    }
            is (I2C_CMD_READ)  { bitState := s_bit_rd_a    }
          }
          sdaChk := false.B
        }

        is (s_bit_start_a) {
          bitState  := s_bit_start_b
          sclOen    := sclOen
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_start_b) {
          bitState  := s_bit_start_c
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_start_c) {
          bitState  := s_bit_start_d
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_start_d) {
          bitState  := s_bit_start_e
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_start_e) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := false.B
          sdaOen    := false.B
          sdaChk    := false.B
        }

        is (s_bit_stop_a) {
          bitState  := s_bit_stop_b
          sclOen    := false.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_stop_b) {
          bitState  := s_bit_stop_c
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_stop_c) {
          bitState  := s_bit_stop_d
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_stop_d) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }

        is (s_bit_rd_a) {
          bitState  := s_bit_rd_b
          sclOen    := false.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_rd_b) {
          bitState  := s_bit_rd_c
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_rd_c) {
          bitState  := s_bit_rd_d
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_rd_d) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := false.B
          sdaOen    := true.B
          sdaChk    := false.B
        }

        is (s_bit_wr_a) {
          bitState  := s_bit_wr_b
          sclOen    := false.B
          sdaOen    := transmitBit
          sdaChk    := false.B
        }
        is (s_bit_wr_b) {
          bitState  := s_bit_wr_c
          sclOen    := true.B
          sdaOen    := transmitBit
          sdaChk    := false.B
        }
        is (s_bit_wr_c) {
          bitState  := s_bit_wr_d
          sclOen    := true.B
          sdaOen    := transmitBit
          sdaChk    := true.B
        }
        is (s_bit_wr_d) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := false.B
          sdaOen    := transmitBit
          sdaChk    := false.B
        }
      }
    }
  }


  //////// Byte level ///////
  val load        = RegInit(false.B)                         // load shift register
  val shift       = RegInit(false.B)                         // shift shift register
  val cmdAck      = RegInit(false.B)                         // also done
  val receivedAck = RegInit(false.B)                         // from I2C slave
  val go          = (cmd.read | cmd.write | cmd.stop) & !cmdAck

  val bitCnt      = RegInit(0.U(3.W))
  when (load) {
    bitCnt := 0x7.U
  }
  .elsewhen (shift) {
    bitCnt := bitCnt - 1.U
  }
  val bitCntDone  = !(bitCnt.orR)

  // receivedData is used as shift register directly
  when (load) {
    receivedData := transmitData
  }
  .elsewhen (shift) {
    receivedData := Cat(receivedData, receivedBit)
  }

  val (s_byte_idle :: s_byte_start :: s_byte_read :: s_byte_write :: s_byte_ack :: s_byte_stop :: Nil) = Enum(6)
  val byteState   = RegInit(s_byte_idle)

  when (arbLost) {
    bitCmd      := I2C_CMD_NOP
    transmitBit := false.B
    shift       := false.B
    load        := false.B
    cmdAck      := false.B
    byteState   := s_byte_idle
    receivedAck := false.B
  }
  .otherwise {
    transmitBit := receivedData(7)
    shift       := false.B
    load        := false.B
    cmdAck      := false.B

    switch (byteState) {
      is (s_byte_idle) {
        when (go) {
          when (cmd.start) {
            byteState := s_byte_start
            bitCmd    := I2C_CMD_START
          }
          .elsewhen (cmd.read) {
            byteState := s_byte_read
            bitCmd    := I2C_CMD_READ
          }
          .elsewhen (cmd.write) {
            byteState := s_byte_write
            bitCmd    := I2C_CMD_WRITE
          }
          .otherwise { // stop
            byteState := s_byte_stop
            bitCmd    := I2C_CMD_STOP
          }

          load        := true.B
        }
      }
      is (s_byte_start) {
        when (bitCmdAck) {
          when (cmd.read) {
            byteState := s_byte_read
            bitCmd    := I2C_CMD_READ
          }
          .otherwise {
            byteState := s_byte_write
            bitCmd    := I2C_CMD_WRITE
          }

          load        := true.B
        }
      }
      is (s_byte_write) {
        when (bitCmdAck) {
          when (bitCntDone) {
            byteState := s_byte_ack
            bitCmd    := I2C_CMD_READ
          }
          .otherwise {
            byteState := s_byte_write
            bitCmd    := I2C_CMD_WRITE
            shift     := true.B
          }
        }
      }
      is (s_byte_read) {
        when (bitCmdAck) {
          when (bitCntDone) {
            byteState := s_byte_ack
            bitCmd    := I2C_CMD_WRITE
          }
          .otherwise {
            byteState := s_byte_read
            bitCmd    := I2C_CMD_READ
          }

          shift       := true.B
          transmitBit := cmd.ack
        }
      }
      is (s_byte_ack) {
        when (bitCmdAck) {
          when (cmd.stop) {
            byteState := s_byte_stop
            bitCmd    := I2C_CMD_STOP
          }
          .otherwise {
            byteState := s_byte_idle
            bitCmd    := I2C_CMD_NOP

	    // generate command acknowledge signal
            cmdAck    := true.B
          }

	  // assign ack_out output to bit_controller_rxd (contains last received bit)
          receivedAck := receivedBit

          transmitBit := true.B
        }
        .otherwise {
          transmitBit := cmd.ack
        }
      }
      is (s_byte_stop) {
        when (bitCmdAck) {
          byteState := s_byte_idle
          bitCmd    := I2C_CMD_NOP

	  // assign ack_out output to bit_controller_rxd (contains last received bit)
          cmdAck    := true.B
        }
      }
    }
  }


  //////// Top level ////////

  // hack: b/c the same register offset is used to write cmd and read status
  val nextCmd = Wire(UInt(8.W))
  cmd := nextCmd.asTypeOf(new CommandBundle)
  nextCmd := cmd.asUInt & 0xFE.U  // clear IRQ_ACK bit (essentially 1 cycle pulse b/c it is overwritten by regmap below)

  // Note: This wins over the regmap update of nextCmd (even if something tries to write them to 1, these values take priority).
  when (cmdAck || arbLost) {
    cmd.start := false.B    // clear command bits when done
    cmd.stop  := false.B    // or when aribitration lost
    cmd.read  := false.B
    cmd.write := false.B
  }

  status.receivedAck := receivedAck
  when (stopCond) {
    status.busy             := false.B
  }
  .elsewhen (startCond) {
    status.busy             := true.B
  }

  when (arbLost) {
    status.arbLost          := true.B
  }
  .elsewhen (cmd.start) {
    status.arbLost          := false.B
  }
  status.transferInProgress := cmd.read || cmd.write
  status.irqFlag            := (cmdAck || arbLost || status.irqFlag) && !cmd.irqAck // interrupt request flag is always generated


  val statusReadReady = RegInit(true.B)
  when (cmdAck || arbLost) {    // => cmd.read or cmd.write deassert 1 cycle later => transferInProgress deassert 2 cycles later
    statusReadReady := false.B  // do not allow status read if status.transferInProgress is going to change
  }
  .elsewhen (!statusReadReady) {
    statusReadReady := true.B
  }

  // statusReadReady,
  regmap(
    I2CCtrlRegs.prescaler_lo -> Seq(RegField(8, prescaler.lo,
                                    RegFieldDesc("prescaler_lo","I2C prescaler, low byte", reset=Some(0)))),
    I2CCtrlRegs.prescaler_hi -> Seq(RegField(8, prescaler.hi,
                                    RegFieldDesc("prescaler_hi","I2C prescaler, high byte", reset=Some(0)))),
    I2CCtrlRegs.control      -> RegFieldGroup("control",
                                Some("Control"),
				control.elements.map{
				    case(name, e) => RegField(e.getWidth,
				                              e.asInstanceOf[UInt],
							      RegFieldDesc(name, s"Sets the ${name}" ,
							      reset=Some(0)))
				}.toSeq),
    I2CCtrlRegs.data         -> Seq(RegField(8, r = RegReadFn(receivedData),  w = RegWriteFn(transmitData),
                                RegFieldDesc("data","I2C tx and rx data", volatile=true, reset=Some(0)))),
    I2CCtrlRegs.cmd_status   -> Seq(RegField(8, r = RegReadFn{ ready =>
                                                               (statusReadReady, status.asUInt)
                                                             },
                                                w = RegWriteFn((valid, data) => {
                                                               when (valid) {
                                                                 statusReadReady := false.B
                                                                 nextCmd := data
                                                             }
                                                             true.B
                                                }),
                                    RegFieldDesc("cmd_status","On write, update I2C command.  On Read, report I2C status", volatile=true)))
  )

  // tie off unused bits
  control.reserved := 0.U
  cmd.reserved     := 0.U
  status.reserved  := 0.U

  interrupts(0) := status.irqFlag & control.intEn
}}

class TLI2C(busWidthBytes: Int, params: I2CParams)(implicit p: Parameters)
  extends I2C(busWidthBytes, params) with HasTLControlRegMap

case class I2CLocated(loc: HierarchicalLocation) extends Field[Seq[I2CAttachParams]](Nil)

case class I2CAttachParams(
  device: I2CParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLI2C = where {
    val name = s"i2c_${I2C.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val i2cClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val i2c = i2cClockDomainWrapper { LazyModule(new TLI2C(tlbus.beatBytes, device)) }
    i2c.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      i2cClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(i2c.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val i2cClockGroup = ClockGroup()
          i2cClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := i2cClockGroup } .getOrElse { i2cClockGroup }
      })

      (i2c.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := i2c.intXing(intXType)

    i2c
  }
}

object I2C {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[I2CPort], name: String)(implicit p: Parameters): ModuleValue[I2CPort] = {
    val i2cNode = node.makeSink()
    InModuleBody { i2cNode.makeIO()(ValName(name)) }
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
