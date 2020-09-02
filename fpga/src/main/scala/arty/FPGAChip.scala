// See LICENSE for license details.
package sifive.freedom.everywhere.e300artydevkit

import Chisel._
import chisel3.core.{attach}
import chisel3.experimental.{withClockAndReset}

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy.{LazyModule}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}
import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

//-------------------------------------------------------------------------
// E300ArtyDevKitFPGAChip
//-------------------------------------------------------------------------

class E300ArtyDevKitFPGAChip(implicit override val p: Parameters) extends ArtyShell {

  //-----------------------------------------------------------------------
  // Clock divider
  //-----------------------------------------------------------------------
  val slow_clock = Wire(Bool())

  // Divide clock by 256, used to generate 32.768 kHz clock for AON block
  withClockAndReset(clock_8MHz, ~mmcm_locked) {
    val clockToggleReg = RegInit(false.B)
    val (_, slowTick) = Counter(true.B, 256)
    when (slowTick) {clockToggleReg := ~clockToggleReg}
    slow_clock := clockToggleReg
  }

  //-----------------------------------------------------------------------
  // DUT
  //-----------------------------------------------------------------------

  withClockAndReset(clock_32MHz, ck_rst) {
    val dut = Module(new E300ArtyDevKitPlatform)

    //---------------------------------------------------------------------
    // SPI flash IOBUFs
    //---------------------------------------------------------------------

    IOBUF(qspi_sck, dut.io.pins.qspi.sck)
    IOBUF(qspi_cs,  dut.io.pins.qspi.cs(0))

    IOBUF(qspi_dq(0), dut.io.pins.qspi.dq(0))
    IOBUF(qspi_dq(1), dut.io.pins.qspi.dq(1))
    IOBUF(qspi_dq(2), dut.io.pins.qspi.dq(2))
    IOBUF(qspi_dq(3), dut.io.pins.qspi.dq(3))

    //---------------------------------------------------------------------
    // JTAG IOBUFs
    //---------------------------------------------------------------------

    dut.io.pins.jtag.TCK.i.ival := IBUFG(IOBUF(jd_2).asClock).asUInt

    IOBUF(jd_5, dut.io.pins.jtag.TMS)
    PULLUP(jd_5)

    IOBUF(jd_4, dut.io.pins.jtag.TDI)
    PULLUP(jd_4)

    IOBUF(jd_0, dut.io.pins.jtag.TDO)

    // mimic putting a pullup on this line (part of reset vote)
    SRST_n := IOBUF(jd_6)
    PULLUP(jd_6)

    // jtag reset
    val jtag_power_on_reset = PowerOnResetFPGAOnly(clock_32MHz)
    dut.io.jtag_reset := jtag_power_on_reset

    // debug reset
    dut_ndreset := dut.io.ndreset

    //---------------------------------------------------------------------
    // Assignment to package pins
    //---------------------------------------------------------------------
    // Pins IO0-IO13
    //
    // FTDI UART TX/RX are not connected to ck_io[0,1]
    // the way they are on Arduino boards.  We copy outgoing
    // data to both places, switch 3 (sw[3]) determines whether
    // input to UART comes from FTDI chip or gpio_16 (shield pin PD0)

    val iobuf_ck0 = Module(new IOBUF())
    iobuf_ck0.io.I := dut.io.pins.gpio.pins(16).o.oval
    iobuf_ck0.io.T := ~dut.io.pins.gpio.pins(16).o.oe
    attach(iobuf_ck0.io.IO, ck_io(0))   // UART0 RX

    val iobuf_uart_txd = Module(new IOBUF())
    iobuf_uart_txd.io.I := dut.io.pins.gpio.pins(16).o.oval
    iobuf_uart_txd.io.T := ~dut.io.pins.gpio.pins(16).o.oe
    attach(iobuf_uart_txd.io.IO, uart_txd_in)

    // gpio(16) input is shared between FTDI TX pin and the Arduino shield pin using SW[3]
    val sw_3_in = IOBUF(sw_3)
    dut.io.pins.gpio.pins(16).i.ival := Mux(sw_3_in,
                                            iobuf_ck0.io.O & dut.io.pins.gpio.pins(16).o.ie,
                                            iobuf_uart_txd.io.O & dut.io.pins.gpio.pins(16).o.ie)

    IOBUF(uart_rxd_out, dut.io.pins.gpio.pins(17))

    // Shield header row 0: PD2-PD7
    IOBUF(ck_io(2),  dut.io.pins.gpio.pins(18))
    IOBUF(ck_io(3),  dut.io.pins.gpio.pins(19)) // PWM1(1)
    IOBUF(ck_io(4),  dut.io.pins.gpio.pins(20)) // PWM1(0)
    IOBUF(ck_io(5),  dut.io.pins.gpio.pins(21)) // PWM1(2)
    IOBUF(ck_io(6),  dut.io.pins.gpio.pins(22)) // PWM1(3)
    IOBUF(ck_io(7),  dut.io.pins.gpio.pins(23))

    // Header row 1: PB0-PB5
    IOBUF(ck_io(8),  dut.io.pins.gpio.pins(0))  // PWM0(0)
    IOBUF(ck_io(9),  dut.io.pins.gpio.pins(1))  // PWM0(1)
    IOBUF(ck_io(10), dut.io.pins.gpio.pins(2))  // SPI CS(0) / PWM0(2)
    IOBUF(ck_io(11), dut.io.pins.gpio.pins(3))  // SPI MOSI  / PWM0(3)
    IOBUF(ck_io(12), dut.io.pins.gpio.pins(4))  // SPI MISO
    IOBUF(ck_io(13), dut.io.pins.gpio.pins(5))  // SPI SCK

    dut.io.pins.gpio.pins(6).i.ival  := 0.U
    dut.io.pins.gpio.pins(7).i.ival  := 0.U
    dut.io.pins.gpio.pins(8).i.ival  := 0.U

    // Header row 3: A0-A5 (we don't support using them as analog inputs)
    // just treat them as regular digital GPIOs
    IOBUF(ck_io(15), dut.io.pins.gpio.pins(9))  // A1 = CS(2)
    IOBUF(ck_io(16), dut.io.pins.gpio.pins(10)) // A2 = CS(3) / PWM2(0)
    IOBUF(ck_io(17), dut.io.pins.gpio.pins(11)) // A3 = PWM2(1)
    IOBUF(ck_io(18), dut.io.pins.gpio.pins(12)) // A4 = PWM2(2) / SDA
    IOBUF(ck_io(19), dut.io.pins.gpio.pins(13)) // A5 = PWM2(3) / SCL

    // Mirror outputs of GPIOs with PWM peripherals to RGB LEDs on Arty
    // assign RGB LED0 R,G,B inputs = PWM0(1,2,3) when iof_1 is active
    IOBUF(led0_r, dut.io.pins.gpio.pins(1))
    IOBUF(led0_g, dut.io.pins.gpio.pins(2))
    IOBUF(led0_b, dut.io.pins.gpio.pins(3))

    // Note that this is the one which is actually connected on the HiFive/Crazy88
    // Board. Same with RGB LED1 R,G,B inputs = PWM1(1,2,3) when iof_1 is active
    IOBUF(led1_r, dut.io.pins.gpio.pins(19))
    IOBUF(led1_g, dut.io.pins.gpio.pins(21))
    IOBUF(led1_b, dut.io.pins.gpio.pins(22))

    // and RGB LED2 R,G,B inputs = PWM2(1,2,3) when iof_1 is active
    IOBUF(led2_r, dut.io.pins.gpio.pins(11))
    IOBUF(led2_g, dut.io.pins.gpio.pins(12))
    IOBUF(led2_b, dut.io.pins.gpio.pins(13))

    // Only 19 out of 20 shield pins connected to GPIO pins
    // Shield pin A5 (pin 14) left unconnected
    // The buttons are connected to some extra GPIO pins not connected on the
    // HiFive1 board
    IOBUF(btn_0, dut.io.pins.gpio.pins(15))
    IOBUF(btn_1, dut.io.pins.gpio.pins(30))
    IOBUF(btn_2, dut.io.pins.gpio.pins(31))

    val iobuf_btn_3 = Module(new IOBUF())
    iobuf_btn_3.io.I := ~dut.io.pins.aon.pmu.dwakeup_n.o.oval
    iobuf_btn_3.io.T := ~dut.io.pins.aon.pmu.dwakeup_n.o.oe
    attach(btn_3, iobuf_btn_3.io.IO)
    dut.io.pins.aon.pmu.dwakeup_n.i.ival := ~iobuf_btn_3.io.O & dut.io.pins.aon.pmu.dwakeup_n.o.ie

    // UART1 RX/TX pins are assigned to PMOD_D connector pins 0/1
    IOBUF(ja_0, dut.io.pins.gpio.pins(25)) // UART1 TX
    IOBUF(ja_1, dut.io.pins.gpio.pins(24)) // UART1 RX

    // SPI2 pins mapped to 6 pin ICSP connector (standard on later
    // arduinos) These are connected to some extra GPIO pins not connected
    // on the HiFive1 board
    IOBUF(ck_ss,   dut.io.pins.gpio.pins(26))
    IOBUF(ck_mosi, dut.io.pins.gpio.pins(27))
    IOBUF(ck_miso, dut.io.pins.gpio.pins(28))
    IOBUF(ck_sck,  dut.io.pins.gpio.pins(29))

    // Use the LEDs for some more useful debugging things
    IOBUF(led_0, ck_rst)
    IOBUF(led_1, SRST_n)
    IOBUF(led_2, dut.io.pins.aon.pmu.dwakeup_n.i.ival)
    IOBUF(led_3, dut.io.pins.gpio.pins(14))

    //---------------------------------------------------------------------
    // Unconnected inputs
    //---------------------------------------------------------------------

    dut.io.pins.aon.erst_n.i.ival       := ~reset_periph
    dut.io.pins.aon.lfextclk.i.ival     := slow_clock
    dut.io.pins.aon.pmu.vddpaden.i.ival := 1.U
  }
}
