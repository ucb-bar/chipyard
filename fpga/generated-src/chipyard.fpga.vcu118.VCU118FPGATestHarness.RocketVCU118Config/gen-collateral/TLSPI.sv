// Generated by CIRCT unknown git version
// Standard header to adapt well known macros to our needs.
`ifndef RANDOMIZE
  `ifdef RANDOMIZE_REG_INIT
    `define RANDOMIZE
  `endif // RANDOMIZE_REG_INIT
`endif // not def RANDOMIZE
`ifndef RANDOMIZE
  `ifdef RANDOMIZE_MEM_INIT
    `define RANDOMIZE
  `endif // RANDOMIZE_MEM_INIT
`endif // not def RANDOMIZE

// RANDOM may be set to an expression that produces a 32-bit random unsigned value.
`ifndef RANDOM
  `define RANDOM $random
`endif // not def RANDOM

// Users can define 'PRINTF_COND' to add an extra gate to prints.
`ifndef PRINTF_COND_
  `ifdef PRINTF_COND
    `define PRINTF_COND_ (`PRINTF_COND)
  `else  // PRINTF_COND
    `define PRINTF_COND_ 1
  `endif // PRINTF_COND
`endif // not def PRINTF_COND_

// Users can define 'ASSERT_VERBOSE_COND' to add an extra gate to assert error printing.
`ifndef ASSERT_VERBOSE_COND_
  `ifdef ASSERT_VERBOSE_COND
    `define ASSERT_VERBOSE_COND_ (`ASSERT_VERBOSE_COND)
  `else  // ASSERT_VERBOSE_COND
    `define ASSERT_VERBOSE_COND_ 1
  `endif // ASSERT_VERBOSE_COND
`endif // not def ASSERT_VERBOSE_COND_

// Users can define 'STOP_COND' to add an extra gate to stop conditions.
`ifndef STOP_COND_
  `ifdef STOP_COND
    `define STOP_COND_ (`STOP_COND)
  `else  // STOP_COND
    `define STOP_COND_ 1
  `endif // STOP_COND
`endif // not def STOP_COND_

// Users can define INIT_RANDOM as general code that gets injected into the
// initializer block for modules with registers.
`ifndef INIT_RANDOM
  `define INIT_RANDOM
`endif // not def INIT_RANDOM

// If using random initialization, you can also define RANDOMIZE_DELAY to
// customize the delay used, otherwise 0.002 is used.
`ifndef RANDOMIZE_DELAY
  `define RANDOMIZE_DELAY 0.002
`endif // not def RANDOMIZE_DELAY

// Define INIT_RANDOM_PROLOG_ for use in our modules below.
`ifndef INIT_RANDOM_PROLOG_
  `ifdef RANDOMIZE
    `ifdef VERILATOR
      `define INIT_RANDOM_PROLOG_ `INIT_RANDOM
    `else  // VERILATOR
      `define INIT_RANDOM_PROLOG_ `INIT_RANDOM #`RANDOMIZE_DELAY begin end
    `endif // VERILATOR
  `else  // RANDOMIZE
    `define INIT_RANDOM_PROLOG_
  `endif // RANDOMIZE
`endif // not def INIT_RANDOM_PROLOG_

module TLSPI(
  input         clock,
                reset,
                auto_control_xing_in_a_valid,
  input  [2:0]  auto_control_xing_in_a_bits_opcode,
  input  [1:0]  auto_control_xing_in_a_bits_size,
  input  [6:0]  auto_control_xing_in_a_bits_source,
  input  [30:0] auto_control_xing_in_a_bits_address,
  input  [7:0]  auto_control_xing_in_a_bits_mask,
  input  [63:0] auto_control_xing_in_a_bits_data,
  input         auto_control_xing_in_d_ready,
                auto_io_out_dq_0_i,
                auto_io_out_dq_1_i,
                auto_io_out_dq_2_i,
                auto_io_out_dq_3_i,
  output        auto_int_xing_out_sync_0,
                auto_control_xing_in_a_ready,
                auto_control_xing_in_d_valid,
  output [2:0]  auto_control_xing_in_d_bits_opcode,
  output [1:0]  auto_control_xing_in_d_bits_size,
  output [6:0]  auto_control_xing_in_d_bits_source,
  output [63:0] auto_control_xing_in_d_bits_data,
  output        auto_io_out_sck,
                auto_io_out_dq_0_o,
                auto_io_out_dq_0_ie,
                auto_io_out_dq_0_oe,
                auto_io_out_dq_1_o,
                auto_io_out_dq_1_ie,
                auto_io_out_dq_1_oe,
                auto_io_out_dq_2_o,
                auto_io_out_dq_2_ie,
                auto_io_out_dq_2_oe,
                auto_io_out_dq_3_o,
                auto_io_out_dq_3_ie,
                auto_io_out_dq_3_oe,
                auto_io_out_cs_0
);

  wire              out_woready_15;	// @[RegisterRouter.scala:83:24]
  wire              _out_wofireMux_T;	// @[RegisterRouter.scala:83:24]
  wire              out_backSel_9;	// @[RegisterRouter.scala:83:24]
  wire              quash;	// @[RegMapFIFO.scala:26:26]
  wire              _mac_io_link_tx_ready;	// @[TLSPI.scala:69:19]
  wire              _mac_io_link_rx_valid;	// @[TLSPI.scala:69:19]
  wire [7:0]        _mac_io_link_rx_bits;	// @[TLSPI.scala:69:19]
  wire              _fifo_io_link_tx_valid;	// @[TLSPI.scala:68:20]
  wire [7:0]        _fifo_io_link_tx_bits;	// @[TLSPI.scala:68:20]
  wire [7:0]        _fifo_io_link_cnt;	// @[TLSPI.scala:68:20]
  wire [1:0]        _fifo_io_link_fmt_proto;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_link_fmt_endian;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_link_fmt_iodir;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_link_cs_set;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_link_cs_clear;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_tx_ready;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_rx_valid;	// @[TLSPI.scala:68:20]
  wire [7:0]        _fifo_io_rx_bits;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_ip_txwm;	// @[TLSPI.scala:68:20]
  wire              _fifo_io_ip_rxwm;	// @[TLSPI.scala:68:20]
  reg  [1:0]        ctrl_fmt_proto;	// @[TLSPI.scala:67:17]
  reg               ctrl_fmt_endian;	// @[TLSPI.scala:67:17]
  reg               ctrl_fmt_iodir;	// @[TLSPI.scala:67:17]
  reg  [3:0]        ctrl_fmt_len;	// @[TLSPI.scala:67:17]
  reg  [11:0]       ctrl_sck_div;	// @[TLSPI.scala:67:17]
  reg               ctrl_sck_pol;	// @[TLSPI.scala:67:17]
  reg               ctrl_sck_pha;	// @[TLSPI.scala:67:17]
  reg               ctrl_cs_id;	// @[TLSPI.scala:67:17]
  reg               ctrl_cs_dflt_0;	// @[TLSPI.scala:67:17]
  reg  [1:0]        ctrl_cs_mode;	// @[TLSPI.scala:67:17]
  reg  [7:0]        ctrl_dla_cssck;	// @[TLSPI.scala:67:17]
  reg  [7:0]        ctrl_dla_sckcs;	// @[TLSPI.scala:67:17]
  reg  [7:0]        ctrl_dla_intercs;	// @[TLSPI.scala:67:17]
  reg  [7:0]        ctrl_dla_interxfr;	// @[TLSPI.scala:67:17]
  reg  [3:0]        ctrl_wm_tx;	// @[TLSPI.scala:67:17]
  reg  [3:0]        ctrl_wm_rx;	// @[TLSPI.scala:67:17]
  reg  [11:0]       ctrl_extradel_coarse;	// @[TLSPI.scala:67:17]
  reg  [4:0]        ctrl_sampledel_sd;	// @[TLSPI.scala:67:17]
  reg               ie_txwm;	// @[TLSPI.scala:81:15]
  reg               ie_rxwm;	// @[TLSPI.scala:81:15]
  wire              out_front_bits_read = auto_control_xing_in_a_bits_opcode == 3'h4;	// @[RegisterRouter.scala:72:36]
  wire              _out_out_bits_data_WIRE_14 = auto_control_xing_in_a_bits_address[11:7] == 5'h0;	// @[Edges.scala:192:34, RegisterRouter.scala:73:19, :83:24]
  assign quash = out_woready_15 & auto_control_xing_in_a_bits_mask[3] & auto_control_xing_in_a_bits_data[31];	// @[Bitwise.scala:28:17, RegMapFIFO.scala:26:26, RegisterRouter.scala:83:24]
  assign out_backSel_9 = auto_control_xing_in_a_bits_address[6:3] == 4'h9;	// @[Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
  assign _out_wofireMux_T = auto_control_xing_in_a_valid & auto_control_xing_in_d_ready;	// @[RegisterRouter.scala:83:24]
  wire              _out_wofireMux_T_2 = _out_wofireMux_T & ~out_front_bits_read;	// @[RegisterRouter.scala:72:36, :83:24]
  wire              out_woready_2 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'h0 & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, RegisterRouter.scala:83:24]
  wire              out_woready_20 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'h2 & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
  wire              out_woready_6 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'h5 & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
  wire              out_woready_22 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'h7 & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
  wire              out_woready_27 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'h8 & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, RegisterRouter.scala:83:24, SPIBundle.scala:86:18]
  assign out_woready_15 = _out_wofireMux_T_2 & out_backSel_9 & _out_out_bits_data_WIRE_14;	// @[RegisterRouter.scala:83:24]
  wire              out_woready_8 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'hA & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
  wire              out_woready_10 = _out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'hE & _out_out_bits_data_WIRE_14;	// @[Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
  wire [15:0]       _GEN = {{1'h1}, {_out_out_bits_data_WIRE_14}, {1'h1}, {1'h1}, {1'h1}, {_out_out_bits_data_WIRE_14}, {_out_out_bits_data_WIRE_14}, {_out_out_bits_data_WIRE_14}, {_out_out_bits_data_WIRE_14}, {1'h1}, {_out_out_bits_data_WIRE_14}, {1'h1}, {_out_out_bits_data_WIRE_14}, {_out_out_bits_data_WIRE_14}, {1'h1}, {_out_out_bits_data_WIRE_14}};	// @[Buffer.scala:47:20, MuxLiteral.scala:49:10, RegisterRouter.scala:83:24]
  wire [15:0][63:0] _GEN_0 = {{64'h0}, {{30'h0, _fifo_io_ip_rxwm, _fifo_io_ip_txwm, 30'h0, ie_rxwm, ie_txwm}}, {64'h0}, {64'h0}, {64'h0}, {{28'h0, ctrl_wm_rx, 28'h0, ctrl_wm_tx}}, {{~_fifo_io_rx_valid, 23'h0, _fifo_io_rx_bits, ~_fifo_io_tx_ready, 31'h0}}, {{44'h0, ctrl_fmt_len, 12'h0, ctrl_fmt_iodir, ctrl_fmt_endian, ctrl_fmt_proto}}, {{27'h0, ctrl_sampledel_sd, 20'h0, ctrl_extradel_coarse}}, {64'h0}, {{8'h0, ctrl_dla_interxfr, 8'h0, ctrl_dla_intercs, 8'h0, ctrl_dla_sckcs, 8'h0, ctrl_dla_cssck}}, {64'h0}, {{62'h0, ctrl_cs_mode}}, {{31'h0, ctrl_cs_dflt_0, 31'h0, ctrl_cs_id}}, {64'h0}, {{30'h0, ctrl_sck_pol, ctrl_sck_pha, 20'h0, ctrl_sck_div}}};	// @[Bundles.scala:259:74, Cat.scala:33:92, MuxLiteral.scala:49:{10,48}, RegMapFIFO.scala:24:9, :45:21, RegisterRouter.scala:83:24, TLSPI.scala:67:17, :68:20, :81:15]
  wire [11:0]       _out_womask_T_21 = {{4{auto_control_xing_in_a_bits_mask[1]}}, {8{auto_control_xing_in_a_bits_mask[0]}}};	// @[Bitwise.scala:28:17, :77:12, RegisterRouter.scala:83:24]
  always @(posedge clock) begin
    if (reset) begin
      ctrl_fmt_proto <= 2'h0;	// @[Bundles.scala:259:74, TLSPI.scala:67:17]
      ctrl_fmt_endian <= 1'h0;	// @[TLSPI.scala:67:17]
      ctrl_fmt_iodir <= 1'h0;	// @[TLSPI.scala:67:17]
      ctrl_fmt_len <= 4'h8;	// @[SPIBundle.scala:86:18, TLSPI.scala:67:17]
      ctrl_sck_div <= 12'h3;	// @[SPIBundle.scala:87:18, TLSPI.scala:67:17]
      ctrl_sck_pol <= 1'h0;	// @[TLSPI.scala:67:17]
      ctrl_sck_pha <= 1'h0;	// @[TLSPI.scala:67:17]
      ctrl_cs_id <= 1'h0;	// @[TLSPI.scala:67:17]
      ctrl_cs_dflt_0 <= 1'h1;	// @[Buffer.scala:47:20, TLSPI.scala:67:17]
      ctrl_cs_mode <= 2'h0;	// @[Bundles.scala:259:74, TLSPI.scala:67:17]
      ctrl_dla_cssck <= 8'h1;	// @[SPIBundle.scala:93:20, TLSPI.scala:67:17]
      ctrl_dla_sckcs <= 8'h1;	// @[SPIBundle.scala:93:20, TLSPI.scala:67:17]
      ctrl_dla_intercs <= 8'h1;	// @[SPIBundle.scala:93:20, TLSPI.scala:67:17]
      ctrl_dla_interxfr <= 8'h0;	// @[Bundles.scala:259:74, TLSPI.scala:67:17]
      ctrl_wm_tx <= 4'h0;	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      ctrl_wm_rx <= 4'h0;	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      ctrl_extradel_coarse <= 12'h0;	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      ctrl_sampledel_sd <= 5'h3;	// @[SPIBundle.scala:101:23, TLSPI.scala:67:17]
      ie_txwm <= 1'h0;	// @[TLSPI.scala:81:15]
      ie_rxwm <= 1'h0;	// @[TLSPI.scala:81:15]
    end
    else begin
      if (out_woready_27 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_fmt_proto <= auto_control_xing_in_a_bits_data[1:0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_27 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_fmt_endian <= auto_control_xing_in_a_bits_data[2];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_27 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_fmt_iodir <= auto_control_xing_in_a_bits_data[3];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_27 & auto_control_xing_in_a_bits_mask[2])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_fmt_len <= auto_control_xing_in_a_bits_data[19:16];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_2 & (&_out_womask_T_21))	// @[RegisterRouter.scala:83:24]
        ctrl_sck_div <= auto_control_xing_in_a_bits_data[11:0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_2 & auto_control_xing_in_a_bits_mask[4])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_sck_pol <= auto_control_xing_in_a_bits_data[33];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_2 & auto_control_xing_in_a_bits_mask[4])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_sck_pha <= auto_control_xing_in_a_bits_data[32];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_20 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_cs_id <= auto_control_xing_in_a_bits_data[0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_20 & auto_control_xing_in_a_bits_mask[4])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_cs_dflt_0 <= auto_control_xing_in_a_bits_data[32];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (_out_wofireMux_T_2 & auto_control_xing_in_a_bits_address[6:3] == 4'h3 & _out_out_bits_data_WIRE_14 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, Cat.scala:33:92, OneHot.scala:57:35, RegisterRouter.scala:83:24]
        ctrl_cs_mode <= auto_control_xing_in_a_bits_data[1:0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_6 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_dla_cssck <= auto_control_xing_in_a_bits_data[7:0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_6 & auto_control_xing_in_a_bits_mask[2])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_dla_sckcs <= auto_control_xing_in_a_bits_data[23:16];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_6 & auto_control_xing_in_a_bits_mask[4])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_dla_intercs <= auto_control_xing_in_a_bits_data[39:32];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_6 & auto_control_xing_in_a_bits_mask[6])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_dla_interxfr <= auto_control_xing_in_a_bits_data[55:48];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_8 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_wm_tx <= auto_control_xing_in_a_bits_data[3:0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_8 & auto_control_xing_in_a_bits_mask[4])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_wm_rx <= auto_control_xing_in_a_bits_data[35:32];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_22 & (&_out_womask_T_21))	// @[RegisterRouter.scala:83:24]
        ctrl_extradel_coarse <= auto_control_xing_in_a_bits_data[11:0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_22 & auto_control_xing_in_a_bits_mask[4])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ctrl_sampledel_sd <= auto_control_xing_in_a_bits_data[36:32];	// @[RegisterRouter.scala:83:24, TLSPI.scala:67:17]
      if (out_woready_10 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ie_txwm <= auto_control_xing_in_a_bits_data[0];	// @[RegisterRouter.scala:83:24, TLSPI.scala:81:15]
      if (out_woready_10 & auto_control_xing_in_a_bits_mask[0])	// @[Bitwise.scala:28:17, RegisterRouter.scala:83:24]
        ie_rxwm <= auto_control_xing_in_a_bits_data[1];	// @[RegisterRouter.scala:83:24, TLSPI.scala:81:15]
    end
  end // always @(posedge)
  `ifndef SYNTHESIS
    `ifdef FIRRTL_BEFORE_INITIAL
      `FIRRTL_BEFORE_INITIAL
    `endif // FIRRTL_BEFORE_INITIAL
    logic [31:0] _RANDOM_0;
    logic [31:0] _RANDOM_1;
    logic [31:0] _RANDOM_2;
    initial begin
      `ifdef INIT_RANDOM_PROLOG_
        `INIT_RANDOM_PROLOG_
      `endif // INIT_RANDOM_PROLOG_
      `ifdef RANDOMIZE_REG_INIT
        _RANDOM_0 = `RANDOM;
        _RANDOM_1 = `RANDOM;
        _RANDOM_2 = `RANDOM;
        ctrl_fmt_proto = _RANDOM_0[1:0];	// @[TLSPI.scala:67:17]
        ctrl_fmt_endian = _RANDOM_0[2];	// @[TLSPI.scala:67:17]
        ctrl_fmt_iodir = _RANDOM_0[3];	// @[TLSPI.scala:67:17]
        ctrl_fmt_len = _RANDOM_0[7:4];	// @[TLSPI.scala:67:17]
        ctrl_sck_div = _RANDOM_0[19:8];	// @[TLSPI.scala:67:17]
        ctrl_sck_pol = _RANDOM_0[20];	// @[TLSPI.scala:67:17]
        ctrl_sck_pha = _RANDOM_0[21];	// @[TLSPI.scala:67:17]
        ctrl_cs_id = _RANDOM_0[22];	// @[TLSPI.scala:67:17]
        ctrl_cs_dflt_0 = _RANDOM_0[23];	// @[TLSPI.scala:67:17]
        ctrl_cs_mode = _RANDOM_0[25:24];	// @[TLSPI.scala:67:17]
        ctrl_dla_cssck = {_RANDOM_0[31:26], _RANDOM_1[1:0]};	// @[TLSPI.scala:67:17]
        ctrl_dla_sckcs = _RANDOM_1[9:2];	// @[TLSPI.scala:67:17]
        ctrl_dla_intercs = _RANDOM_1[17:10];	// @[TLSPI.scala:67:17]
        ctrl_dla_interxfr = _RANDOM_1[25:18];	// @[TLSPI.scala:67:17]
        ctrl_wm_tx = _RANDOM_1[29:26];	// @[TLSPI.scala:67:17]
        ctrl_wm_rx = {_RANDOM_1[31:30], _RANDOM_2[1:0]};	// @[TLSPI.scala:67:17]
        ctrl_extradel_coarse = _RANDOM_2[13:2];	// @[TLSPI.scala:67:17]
        ctrl_sampledel_sd = _RANDOM_2[18:14];	// @[TLSPI.scala:67:17]
        ie_txwm = _RANDOM_2[19];	// @[TLSPI.scala:67:17, :81:15]
        ie_rxwm = _RANDOM_2[20];	// @[TLSPI.scala:67:17, :81:15]
      `endif // RANDOMIZE_REG_INIT
    end // initial
    `ifdef FIRRTL_AFTER_INITIAL
      `FIRRTL_AFTER_INITIAL
    `endif // FIRRTL_AFTER_INITIAL
  `endif // not def SYNTHESIS
  IntSyncCrossingSource_1 intsource (	// @[Crossing.scala:28:31]
    .clock           (clock),
    .reset           (reset),
    .auto_in_0       (_fifo_io_ip_txwm & ie_txwm | _fifo_io_ip_rxwm & ie_rxwm),	// @[TLSPI.scala:68:20, :81:15, :83:{35,47,59}]
    .auto_out_sync_0 (auto_int_xing_out_sync_0)
  );
  SPIFIFO fifo (	// @[TLSPI.scala:68:20]
    .clock              (clock),
    .reset              (reset),
    .io_ctrl_fmt_proto  (ctrl_fmt_proto),	// @[TLSPI.scala:67:17]
    .io_ctrl_fmt_endian (ctrl_fmt_endian),	// @[TLSPI.scala:67:17]
    .io_ctrl_fmt_iodir  (ctrl_fmt_iodir),	// @[TLSPI.scala:67:17]
    .io_ctrl_fmt_len    (ctrl_fmt_len),	// @[TLSPI.scala:67:17]
    .io_ctrl_cs_mode    (ctrl_cs_mode),	// @[TLSPI.scala:67:17]
    .io_ctrl_wm_tx      (ctrl_wm_tx),	// @[TLSPI.scala:67:17]
    .io_ctrl_wm_rx      (ctrl_wm_rx),	// @[TLSPI.scala:67:17]
    .io_link_tx_ready   (_mac_io_link_tx_ready),	// @[TLSPI.scala:69:19]
    .io_link_rx_valid   (_mac_io_link_rx_valid),	// @[TLSPI.scala:69:19]
    .io_link_rx_bits    (_mac_io_link_rx_bits),	// @[TLSPI.scala:69:19]
    .io_tx_valid        (out_woready_15 & auto_control_xing_in_a_bits_mask[0] & ~quash),	// @[Bitwise.scala:28:17, RegMapFIFO.scala:18:{30,33}, :26:26, RegisterRouter.scala:83:24]
    .io_tx_bits         (auto_control_xing_in_a_bits_data[7:0]),	// @[RegisterRouter.scala:83:24]
    .io_rx_ready        (_out_wofireMux_T & out_front_bits_read & out_backSel_9 & _out_out_bits_data_WIRE_14 & auto_control_xing_in_a_bits_mask[4]),	// @[Bitwise.scala:28:17, RegisterRouter.scala:72:36, :83:24]
    .io_link_tx_valid   (_fifo_io_link_tx_valid),
    .io_link_tx_bits    (_fifo_io_link_tx_bits),
    .io_link_cnt        (_fifo_io_link_cnt),
    .io_link_fmt_proto  (_fifo_io_link_fmt_proto),
    .io_link_fmt_endian (_fifo_io_link_fmt_endian),
    .io_link_fmt_iodir  (_fifo_io_link_fmt_iodir),
    .io_link_cs_set     (_fifo_io_link_cs_set),
    .io_link_cs_clear   (_fifo_io_link_cs_clear),
    .io_tx_ready        (_fifo_io_tx_ready),
    .io_rx_valid        (_fifo_io_rx_valid),
    .io_rx_bits         (_fifo_io_rx_bits),
    .io_ip_txwm         (_fifo_io_ip_txwm),
    .io_ip_rxwm         (_fifo_io_ip_rxwm)
  );
  SPIMedia mac (	// @[TLSPI.scala:69:19]
    .clock                   (clock),
    .reset                   (reset),
    .io_port_dq_0_i          (auto_io_out_dq_0_i),
    .io_port_dq_1_i          (auto_io_out_dq_1_i),
    .io_port_dq_2_i          (auto_io_out_dq_2_i),
    .io_port_dq_3_i          (auto_io_out_dq_3_i),
    .io_ctrl_sck_div         (ctrl_sck_div),	// @[TLSPI.scala:67:17]
    .io_ctrl_sck_pol         (ctrl_sck_pol),	// @[TLSPI.scala:67:17]
    .io_ctrl_sck_pha         (ctrl_sck_pha),	// @[TLSPI.scala:67:17]
    .io_ctrl_dla_cssck       (ctrl_dla_cssck),	// @[TLSPI.scala:67:17]
    .io_ctrl_dla_sckcs       (ctrl_dla_sckcs),	// @[TLSPI.scala:67:17]
    .io_ctrl_dla_intercs     (ctrl_dla_intercs),	// @[TLSPI.scala:67:17]
    .io_ctrl_dla_interxfr    (ctrl_dla_interxfr),	// @[TLSPI.scala:67:17]
    .io_ctrl_cs_id           (ctrl_cs_id),	// @[TLSPI.scala:67:17]
    .io_ctrl_cs_dflt_0       (ctrl_cs_dflt_0),	// @[TLSPI.scala:67:17]
    .io_ctrl_extradel_coarse (ctrl_extradel_coarse),	// @[TLSPI.scala:67:17]
    .io_ctrl_sampledel_sd    (ctrl_sampledel_sd),	// @[TLSPI.scala:67:17]
    .io_link_tx_valid        (_fifo_io_link_tx_valid),	// @[TLSPI.scala:68:20]
    .io_link_tx_bits         (_fifo_io_link_tx_bits),	// @[TLSPI.scala:68:20]
    .io_link_cnt             (_fifo_io_link_cnt),	// @[TLSPI.scala:68:20]
    .io_link_fmt_proto       (_fifo_io_link_fmt_proto),	// @[TLSPI.scala:68:20]
    .io_link_fmt_endian      (_fifo_io_link_fmt_endian),	// @[TLSPI.scala:68:20]
    .io_link_fmt_iodir       (_fifo_io_link_fmt_iodir),	// @[TLSPI.scala:68:20]
    .io_link_cs_set          (_fifo_io_link_cs_set),	// @[TLSPI.scala:68:20]
    .io_link_cs_clear        (_fifo_io_link_cs_clear),	// @[TLSPI.scala:68:20]
    .io_port_sck             (auto_io_out_sck),
    .io_port_dq_0_o          (auto_io_out_dq_0_o),
    .io_port_dq_0_ie         (auto_io_out_dq_0_ie),
    .io_port_dq_0_oe         (auto_io_out_dq_0_oe),
    .io_port_dq_1_o          (auto_io_out_dq_1_o),
    .io_port_dq_1_ie         (auto_io_out_dq_1_ie),
    .io_port_dq_1_oe         (auto_io_out_dq_1_oe),
    .io_port_dq_2_o          (auto_io_out_dq_2_o),
    .io_port_dq_2_ie         (auto_io_out_dq_2_ie),
    .io_port_dq_2_oe         (auto_io_out_dq_2_oe),
    .io_port_dq_3_o          (auto_io_out_dq_3_o),
    .io_port_dq_3_ie         (auto_io_out_dq_3_ie),
    .io_port_dq_3_oe         (auto_io_out_dq_3_oe),
    .io_port_cs_0            (auto_io_out_cs_0),
    .io_link_tx_ready        (_mac_io_link_tx_ready),
    .io_link_rx_valid        (_mac_io_link_rx_valid),
    .io_link_rx_bits         (_mac_io_link_rx_bits)
  );
  assign auto_control_xing_in_a_ready = auto_control_xing_in_d_ready;
  assign auto_control_xing_in_d_valid = auto_control_xing_in_a_valid;
  assign auto_control_xing_in_d_bits_opcode = {2'h0, out_front_bits_read};	// @[Bundles.scala:259:74, RegisterRouter.scala:72:36, :98:19]
  assign auto_control_xing_in_d_bits_size = auto_control_xing_in_a_bits_size;
  assign auto_control_xing_in_d_bits_source = auto_control_xing_in_a_bits_source;
  assign auto_control_xing_in_d_bits_data = _GEN[auto_control_xing_in_a_bits_address[6:3]] ? _GEN_0[auto_control_xing_in_a_bits_address[6:3]] : 64'h0;	// @[Bundles.scala:259:74, Cat.scala:33:92, MuxLiteral.scala:49:10, RegisterRouter.scala:83:24]
endmodule

