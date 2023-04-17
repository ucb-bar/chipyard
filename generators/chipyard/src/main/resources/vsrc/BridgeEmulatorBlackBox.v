
module BridgeEmulatorBlackBox
(
  input clock,
  input reset,
  output        masterPunchThroughIO_0_a_valid,
  output [2:0]  masterPunchThroughIO_0_a_bits_opcode,
                masterPunchThroughIO_0_a_bits_param,
  output [3:0]  masterPunchThroughIO_0_a_bits_size,
  output [1:0]  masterPunchThroughIO_0_a_bits_source,
  output  [31:0] masterPunchThroughIO_0_a_bits_address,
  output  [7:0]  masterPunchThroughIO_0_a_bits_mask,
  output  [63:0] masterPunchThroughIO_0_a_bits_data,
  output         masterPunchThroughIO_0_a_bits_corrupt,
                masterPunchThroughIO_0_d_ready,
                beuIntSlavePunchThroughIO_0_0,
  input        masterPunchThroughIO_0_a_ready,
                masterPunchThroughIO_0_d_valid,
  input [2:0]  masterPunchThroughIO_0_d_bits_opcode,
  input [1:0]  masterPunchThroughIO_0_d_bits_param,
  input [3:0]  masterPunchThroughIO_0_d_bits_size,
  input [1:0]  masterPunchThroughIO_0_d_bits_source,
  input [2:0]  masterPunchThroughIO_0_d_bits_sink,
  input        masterPunchThroughIO_0_d_bits_denied,
  input [63:0] masterPunchThroughIO_0_d_bits_data,
  input        masterPunchThroughIO_0_d_bits_corrupt,
  input [1:0]  hartid,
  output       wfi,
  output       debug,
  output       mtip,
  output       msip,
  output       meip,
  output       seip
);

// Do nothing??
assign masterPunchThroughIO_0_a_valid = 0;
assign masterPunchThroughIO_0_a_bits_opcode = 0;
assign masterPunchThroughIO_0_a_bits_param = 0;
assign masterPunchThroughIO_0_a_bits_size = 0;
assign masterPunchThroughIO_0_a_bits_source = 0;
assign  masterPunchThroughIO_0_a_bits_address = 0;
assign  masterPunchThroughIO_0_a_bits_mask = 0;
assign  masterPunchThroughIO_0_a_bits_data = 0;
assign  masterPunchThroughIO_0_a_bits_corrupt = 0;
assign masterPunchThroughIO_0_d_ready = 0;

assign wfi = 0;
assign debug = 0;
assign mtip = 0;
assign msip = 0;
assign meip = 0;
assign seip = 0;

endmodule
