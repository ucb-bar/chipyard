package chipyard.fpga.arty.e300

import chisel3._
import chisel3.experimental.{attach, IO}

import freechips.rocketchip.util._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem.{NExtTopInterrupts}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.pwm._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.mockaon._
import sifive.blocks.devices.jtag._
import sifive.blocks.devices.pinctrl._

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}
import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipsalliance.rocketchip.config._

import chipyard.iobinders.{OverrideIOBinder, GetSystemParameters}
import chipyard.{HasHarnessSignalReferences}

class WithE300Connections extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripherySPIModuleImp
    with HasPeripheryDebugModuleImp
    with HasPeripheryPWMModuleImp
    with HasPeripherySPIFlashModuleImp
    with HasPeripheryMockAONModuleImp
    with HasPeripheryI2CModuleImp) => {

    implicit val p: Parameters = GetSystemParameters(system)

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    // E300DigitalTop <-> ChipTop connections
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------

    object PinGen {
      def apply(): BasePin = {
        val pin = new BasePin()
        pin
      }
    }

    val io_jtag = IO(new JTAGPins(() => PinGen(), false)).suggestName("jtag")
    val io_gpio = IO(new GPIOPins(() => PinGen(), p(PeripheryGPIOKey)(0))).suggestName("gpio")
    val io_qspi = IO(new SPIPins(() => PinGen(), p(PeripherySPIFlashKey)(0))).suggestName("qspi")
    val io_aon = IO(new MockAONWrapperPins()).suggestName("aon")
    val io_jtag_reset = IO(Input(Bool())).suggestName("jtag_reset")
    val io_ndreset    = IO(Output(Bool())).suggestName("ndreset")

    val io_async_corerst = IO(Input(Bool())).suggestName("core_reset")
    system.reset := ResetCatchAndSync(system.clock, io_async_corerst, 20)
    Debug.connectDebugClockAndReset(system.debug, system.clock)

    //-----------------------------------------------------------------------
    // Check for unsupported rocket-chip connections
    //-----------------------------------------------------------------------

    require (p(NExtTopInterrupts) == 0, "No Top-level interrupts supported");

    //-----------------------------------------------------------------------
    // Build GPIO Pin Mux
    //-----------------------------------------------------------------------
    // Pin Mux for UART, SPI, PWM
    // First convert the System outputs into "IOF" using the respective *GPIOPort
    // converters.

    val sys_uart = system.uart
    val sys_pwm  = system.pwm
    val sys_spi  = system.spi
    val sys_i2c  = system.i2c

    val uart_pins = p(PeripheryUARTKey).map { c => Wire(new UARTPins(() => PinGen()))}
    val pwm_pins  = p(PeripheryPWMKey).map  { c => Wire(new PWMPins(() => PinGen(), c))}
    val spi_pins  = p(PeripherySPIKey).map  { c => Wire(new SPIPins(() => PinGen(), c))}
    val i2c_pins  = p(PeripheryI2CKey).map  { c => Wire(new I2CPins(() => PinGen()))}

    (uart_pins zip  sys_uart) map {case (p, r) => UARTPinsFromPort(p, r, clock = system.clock, reset = system.reset.asBool, syncStages = 0)}
    (pwm_pins  zip  sys_pwm)  map {case (p, r) => PWMPinsFromPort(p, r) }
    (spi_pins  zip  sys_spi)  map {case (p, r) => SPIPinsFromPort(p, r, clock = system.clock, reset = system.reset.asBool, syncStages = 0)}
    (i2c_pins  zip  sys_i2c)  map {case (p, r) => I2CPinsFromPort(p, r, clock = system.clock, reset = system.reset.asBool, syncStages = 0)}

    //-----------------------------------------------------------------------
    // Default Pin connections before attaching pinmux

    for (iof_0 <- system.gpio(0).iof_0.get) {
      iof_0.default()
    }

    for (iof_1 <- system.gpio(0).iof_1.get) {
      iof_1.default()
    }

    //-----------------------------------------------------------------------

    val iof_0 = system.gpio(0).iof_0.get
    val iof_1 = system.gpio(0).iof_1.get

    // SPI1 (0 is the dedicated)
    BasePinToIOF(spi_pins(0).cs(0), iof_0(2))
    BasePinToIOF(spi_pins(0).dq(0), iof_0(3))
    BasePinToIOF(spi_pins(0).dq(1), iof_0(4))
    BasePinToIOF(spi_pins(0).sck,   iof_0(5))
    BasePinToIOF(spi_pins(0).dq(2), iof_0(6))
    BasePinToIOF(spi_pins(0).dq(3), iof_0(7))
    BasePinToIOF(spi_pins(0).cs(1), iof_0(8))
    BasePinToIOF(spi_pins(0).cs(2), iof_0(9))
    BasePinToIOF(spi_pins(0).cs(3), iof_0(10))

    // SPI2
    BasePinToIOF(spi_pins(1).cs(0), iof_0(26))
    BasePinToIOF(spi_pins(1).dq(0), iof_0(27))
    BasePinToIOF(spi_pins(1).dq(1), iof_0(28))
    BasePinToIOF(spi_pins(1).sck,   iof_0(29))
    BasePinToIOF(spi_pins(1).dq(2), iof_0(30))
    BasePinToIOF(spi_pins(1).dq(3), iof_0(31))

    // I2C
    if (p(PeripheryI2CKey).length == 1) {
      BasePinToIOF(i2c_pins(0).sda, iof_0(12))
      BasePinToIOF(i2c_pins(0).scl, iof_0(13))
    }

    // UART0
    BasePinToIOF(uart_pins(0).rxd, iof_0(16))
    BasePinToIOF(uart_pins(0).txd, iof_0(17))

    // UART1
    BasePinToIOF(uart_pins(1).rxd, iof_0(24))
    BasePinToIOF(uart_pins(1).txd, iof_0(25))

    //PWM
    BasePinToIOF(pwm_pins(0).pwm(0), iof_1(0) )
    BasePinToIOF(pwm_pins(0).pwm(1), iof_1(1) )
    BasePinToIOF(pwm_pins(0).pwm(2), iof_1(2) )
    BasePinToIOF(pwm_pins(0).pwm(3), iof_1(3) )

    BasePinToIOF(pwm_pins(1).pwm(1), iof_1(19))
    BasePinToIOF(pwm_pins(1).pwm(0), iof_1(20))
    BasePinToIOF(pwm_pins(1).pwm(2), iof_1(21))
    BasePinToIOF(pwm_pins(1).pwm(3), iof_1(22))

    BasePinToIOF(pwm_pins(2).pwm(0), iof_1(10))
    BasePinToIOF(pwm_pins(2).pwm(1), iof_1(11))
    BasePinToIOF(pwm_pins(2).pwm(2), iof_1(12))
    BasePinToIOF(pwm_pins(2).pwm(3), iof_1(13))

    //-----------------------------------------------------------------------
    // Drive actual Pads
    //-----------------------------------------------------------------------

    // Result of Pin Mux
    GPIOPinsFromPort(io_gpio, system.gpio(0))

    // Dedicated SPI Pads
    SPIPinsFromPort(io_qspi, system.qspi(0), clock = system.clock, reset = system.reset.asBool, syncStages = 3)

    // JTAG Debug Interface
    val sjtag = system.debug.get.systemjtag.get
    JTAGPinsFromPort(io_jtag, sjtag.jtag)
    sjtag.reset := io_jtag_reset
    sjtag.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)

    io_ndreset := system.debug.get.ndreset

    // AON Pads -- direct connection is OK because
    // EnhancedPin is hard-coded in MockAONPads
    // and thus there is no .fromPort method.
    io_aon <> system.aon.pins

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    // Harness Function (ArtyHarness <-> ChipTop)
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    val harnessFn = (baseTh: HasHarnessSignalReferences) => {
      baseTh match { case th: ArtyShell =>

        io_async_corerst := th.reset_core

        //-----------------------------------------------------------------------
        // Clock divider
        //-----------------------------------------------------------------------
        val slow_clock = Wire(Bool())

        // Divide clock by 256, used to generate 32.768 kHz clock for AON block
        withClockAndReset(th.clock_8MHz, ~th.mmcm_locked) {
          val clockToggleReg = RegInit(false.B)
          val (_, slowTick) = chisel3.util.Counter(true.B, 256)
          when (slowTick) {clockToggleReg := ~clockToggleReg}
          slow_clock := clockToggleReg
        }

        //-----------------------------------------------------------------------
        // DUT
        //-----------------------------------------------------------------------
        withClockAndReset(th.clock_32MHz, th.ck_rst) {

          //---------------------------------------------------------------------
          // SPI flash IOBUFs
          //---------------------------------------------------------------------

          IOBUF(th.qspi_sck, io_qspi.sck)
          IOBUF(th.qspi_cs,  io_qspi.cs(0))

          IOBUF(th.qspi_dq(0), io_qspi.dq(0))
          IOBUF(th.qspi_dq(1), io_qspi.dq(1))
          IOBUF(th.qspi_dq(2), io_qspi.dq(2))
          IOBUF(th.qspi_dq(3), io_qspi.dq(3))

          //---------------------------------------------------------------------
          // JTAG IOBUFs
          //---------------------------------------------------------------------

          io_jtag.TCK.i.ival := IBUFG(IOBUF(th.jd_2).asClock).asUInt

          IOBUF(th.jd_5, io_jtag.TMS)
          PULLUP(th.jd_5)

          IOBUF(th.jd_4, io_jtag.TDI)
          PULLUP(th.jd_4)

          IOBUF(th.jd_0, io_jtag.TDO)

          // mimic putting a pullup on this line (part of reset vote)
          th.SRST_n := IOBUF(th.jd_6)
          PULLUP(th.jd_6)

          // jtag reset
          val jtag_power_on_reset = PowerOnResetFPGAOnly(th.clock_32MHz)
          io_jtag_reset := jtag_power_on_reset

          // debug reset
          th.dut_ndreset := io_ndreset

          //---------------------------------------------------------------------
          // Assignment to package pins
          //---------------------------------------------------------------------
          // Pins IO0-IO13
          //
          // FTDI UART TX/RX are not connected to th.ck_io[0,1]
          // the way they are on Arduino boards.  We copy outgoing
          // data to both places, switch 3 (sw[3]) determines whether
          // input to UART comes from FTDI chip or gpio_16 (shield pin PD0)

          val iobuf_ck0 = Module(new IOBUF())
          iobuf_ck0.io.I := io_gpio.pins(16).o.oval
          iobuf_ck0.io.T := ~io_gpio.pins(16).o.oe
          attach(iobuf_ck0.io.IO, th.ck_io(0))   // UART0 RX

          val iobuf_uart_txd = Module(new IOBUF())
          iobuf_uart_txd.io.I := io_gpio.pins(16).o.oval
          iobuf_uart_txd.io.T := ~io_gpio.pins(16).o.oe
          attach(iobuf_uart_txd.io.IO, th.uart_txd_in)

          // gpio(16) input is shared between FTDI TX pin and the Arduino shield pin using SW[3]
          val sw_3_in = IOBUF(th.sw_3)
          io_gpio.pins(16).i.ival := Mux(sw_3_in,
                                                  iobuf_ck0.io.O & io_gpio.pins(16).o.ie,
                                                  iobuf_uart_txd.io.O & io_gpio.pins(16).o.ie)

          IOBUF(th.uart_rxd_out, io_gpio.pins(17))

          // Shield header row 0: PD2-PD7
          IOBUF(th.ck_io(2),  io_gpio.pins(18))
          IOBUF(th.ck_io(3),  io_gpio.pins(19)) // PWM1(1)
          IOBUF(th.ck_io(4),  io_gpio.pins(20)) // PWM1(0)
          IOBUF(th.ck_io(5),  io_gpio.pins(21)) // PWM1(2)
          IOBUF(th.ck_io(6),  io_gpio.pins(22)) // PWM1(3)
          IOBUF(th.ck_io(7),  io_gpio.pins(23))

          // Header row 1: PB0-PB5
          IOBUF(th.ck_io(8),  io_gpio.pins(0))  // PWM0(0)
          IOBUF(th.ck_io(9),  io_gpio.pins(1))  // PWM0(1)
          IOBUF(th.ck_io(10), io_gpio.pins(2))  // SPI CS(0) / PWM0(2)
          IOBUF(th.ck_io(11), io_gpio.pins(3))  // SPI MOSI  / PWM0(3)
          IOBUF(th.ck_io(12), io_gpio.pins(4))  // SPI MISO
          IOBUF(th.ck_io(13), io_gpio.pins(5))  // SPI SCK

          io_gpio.pins(6).i.ival  := 0.U
          io_gpio.pins(7).i.ival  := 0.U
          io_gpio.pins(8).i.ival  := 0.U

          // Header row 3: A0-A5 (we don't support using them as analog inputs)
          // just treat them as regular digital GPIOs
          IOBUF(th.ck_io(15), io_gpio.pins(9))  // A1 = CS(2)
          IOBUF(th.ck_io(16), io_gpio.pins(10)) // A2 = CS(3) / PWM2(0)
          IOBUF(th.ck_io(17), io_gpio.pins(11)) // A3 = PWM2(1)
          IOBUF(th.ck_io(18), io_gpio.pins(12)) // A4 = PWM2(2) / SDA
          IOBUF(th.ck_io(19), io_gpio.pins(13)) // A5 = PWM2(3) / SCL

          // Mirror outputs of GPIOs with PWM peripherals to RGB LEDs on Arty
          // assign RGB LED0 R,G,B inputs = PWM0(1,2,3) when iof_1 is active
          IOBUF(th.led0_r, io_gpio.pins(1))
          IOBUF(th.led0_g, io_gpio.pins(2))
          IOBUF(th.led0_b, io_gpio.pins(3))

          // Note that this is the one which is actually connected on the HiFive/Crazy88
          // Board. Same with RGB LED1 R,G,B inputs = PWM1(1,2,3) when iof_1 is active
          IOBUF(th.led1_r, io_gpio.pins(19))
          IOBUF(th.led1_g, io_gpio.pins(21))
          IOBUF(th.led1_b, io_gpio.pins(22))

          // and RGB LED2 R,G,B inputs = PWM2(1,2,3) when iof_1 is active
          IOBUF(th.led2_r, io_gpio.pins(11))
          IOBUF(th.led2_g, io_gpio.pins(12))
          IOBUF(th.led2_b, io_gpio.pins(13))

          // Only 19 out of 20 shield pins connected to GPIO pins
          // Shield pin A5 (pin 14) left unconnected
          // The buttons are connected to some extra GPIO pins not connected on the
          // HiFive1 board
          IOBUF(th.btn_0, io_gpio.pins(15))
          IOBUF(th.btn_1, io_gpio.pins(30))
          IOBUF(th.btn_2, io_gpio.pins(31))

          val iobuf_btn_3 = Module(new IOBUF())
          iobuf_btn_3.io.I := ~io_aon.pmu.dwakeup_n.o.oval
          iobuf_btn_3.io.T := ~io_aon.pmu.dwakeup_n.o.oe
          attach(th.btn_3, iobuf_btn_3.io.IO)
          io_aon.pmu.dwakeup_n.i.ival := ~iobuf_btn_3.io.O & io_aon.pmu.dwakeup_n.o.ie

          // UART1 RX/TX pins are assigned to PMOD_D connector pins 0/1
          IOBUF(th.ja_0, io_gpio.pins(25)) // UART1 TX
          IOBUF(th.ja_1, io_gpio.pins(24)) // UART1 RX

          // SPI2 pins mapped to 6 pin ICSP connector (standard on later
          // arduinos) These are connected to some extra GPIO pins not connected
          // on the HiFive1 board
          IOBUF(th.ck_ss,   io_gpio.pins(26))
          IOBUF(th.ck_mosi, io_gpio.pins(27))
          IOBUF(th.ck_miso, io_gpio.pins(28))
          IOBUF(th.ck_sck,  io_gpio.pins(29))

          // Use the LEDs for some more useful debugging things
          IOBUF(th.led_0, th.ck_rst)
          IOBUF(th.led_1, th.SRST_n)
          IOBUF(th.led_2, io_aon.pmu.dwakeup_n.i.ival)
          IOBUF(th.led_3, io_gpio.pins(14))

          //---------------------------------------------------------------------
          // Unconnected inputs
          //---------------------------------------------------------------------

          io_aon.erst_n.i.ival       := ~th.reset_periph
          io_aon.lfextclk.i.ival     := slow_clock
          io_aon.pmu.vddpaden.i.ival := 1.U
        }

        Nil
      }
    }

    Seq((Nil, Nil, Some(harnessFn)))
  }
})

