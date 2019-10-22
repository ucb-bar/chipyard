// Sha3Accel w/ a blackbox (a dummy DCO) included inside

module Sha3AccelwBB( // @[:example.TestHarness.Sha3RocketConfig.fir@135905.2]
  input         clock, // @[:example.TestHarness.Sha3RocketConfig.fir@135906.4]
  input         reset, // @[:example.TestHarness.Sha3RocketConfig.fir@135907.4]
  output        io_cmd_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [6:0]  io_cmd_bits_inst_funct, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_cmd_bits_rs1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_cmd_bits_rs2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_req_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_req_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [39:0] io_mem_req_bits_addr, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [7:0]  io_mem_req_bits_tag, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [4:0]  io_mem_req_bits_cmd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [63:0] io_mem_req_bits_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_resp_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [7:0]  io_mem_resp_bits_tag, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_mem_resp_bits_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_busy, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [13:0] col_sel_b,
  input  [15:0] row_sel_b,
  input  [7:0]  code_regulator,
  input         dither,
  input         sleep_b,
  output        dco_clock
);
  Sha3Accel sha3 (
    .clock(clock),
    .reset(reset),
    .io_cmd_ready(io_cmd_ready),
    .io_cmd_valid(io_cmd_valid),
    .io_cmd_bits_inst_funct(io_cmd_bits_inst_funct),
    .io_cmd_bits_rs1(io_cmd_bits_rs1),
    .io_cmd_bits_rs2(io_cmd_bits_rs2),
    .io_mem_req_ready(io_mem_req_ready),
    .io_mem_req_valid(io_mem_req_valid),
    .io_mem_req_bits_addr(io_mem_req_bits_addr),
    .io_mem_req_bits_tag(io_mem_req_bits_tag),
    .io_mem_req_bits_cmd(io_mem_req_bits_cmd),
    .io_mem_req_bits_data(io_mem_req_bits_data),
    .io_mem_resp_valid(io_mem_resp_valid),
    .io_mem_resp_bits_tag(io_mem_resp_bits_tag),
    .io_mem_resp_bits_data(io_mem_resp_bits_data),
    .io_busy(io_busy)
  );
  ExampleDCO dco (
    .col_sel_b(col_sel_b),
    .row_sel_b(row_sel_b),
    .code_regulator(code_regulator),
    .dither(dither),
    .sleep_b(sleep_b),
    .clock(dco_clock)
  );
endmodule

module ExampleDCO (
  input  [13:0] col_sel_b,
  input  [15:0] row_sel_b,
  input  [7:0]  code_regulator,
  input         dither,
  input         sleep_b,
  output        clock
);
endmodule
