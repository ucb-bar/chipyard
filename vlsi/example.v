// Extracted Sha3Accel w/ a blackbox (a dummy DCO) included inside

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
  wire clock; // from dummy DCO
  wire  ctrl_clock; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_reset; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_rocc_req_val; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_rocc_req_rdy; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [1:0] ctrl_io_rocc_funct; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [63:0] ctrl_io_rocc_rs1; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [63:0] ctrl_io_rocc_rs2; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_busy; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_dmem_req_val; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_dmem_req_rdy; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [6:0] ctrl_io_dmem_req_tag; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [31:0] ctrl_io_dmem_req_addr; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [4:0] ctrl_io_dmem_req_cmd; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_dmem_resp_val; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [6:0] ctrl_io_dmem_resp_tag; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [63:0] ctrl_io_dmem_resp_data; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [4:0] ctrl_io_round; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_absorb; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [4:0] ctrl_io_aindex; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_init; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  ctrl_io_write; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [2:0] ctrl_io_windex; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire [63:0] ctrl_io_buffer_out; // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
  wire  dpath_clock; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire  dpath_reset; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire  dpath_io_absorb; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire  dpath_io_init; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire  dpath_io_write; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [4:0] dpath_io_round; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [4:0] dpath_io_aindex; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [63:0] dpath_io_message_in; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [63:0] dpath_io_hash_out_0; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [63:0] dpath_io_hash_out_1; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [63:0] dpath_io_hash_out_2; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [63:0] dpath_io_hash_out_3; // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
  wire [1:0] _T; // @[:example.TestHarness.Sha3RocketConfig.fir@135941.4]
  wire [63:0] _GEN_0; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  wire [63:0] _GEN_1; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  wire [63:0] _GEN_2; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  CtrlModule ctrl ( // @[sha3.scala 61:20:example.TestHarness.Sha3RocketConfig.fir@135916.4]
    .clock(ctrl_clock),
    .reset(ctrl_reset),
    .io_rocc_req_val(ctrl_io_rocc_req_val),
    .io_rocc_req_rdy(ctrl_io_rocc_req_rdy),
    .io_rocc_funct(ctrl_io_rocc_funct),
    .io_rocc_rs1(ctrl_io_rocc_rs1),
    .io_rocc_rs2(ctrl_io_rocc_rs2),
    .io_busy(ctrl_io_busy),
    .io_dmem_req_val(ctrl_io_dmem_req_val),
    .io_dmem_req_rdy(ctrl_io_dmem_req_rdy),
    .io_dmem_req_tag(ctrl_io_dmem_req_tag),
    .io_dmem_req_addr(ctrl_io_dmem_req_addr),
    .io_dmem_req_cmd(ctrl_io_dmem_req_cmd),
    .io_dmem_resp_val(ctrl_io_dmem_resp_val),
    .io_dmem_resp_tag(ctrl_io_dmem_resp_tag),
    .io_dmem_resp_data(ctrl_io_dmem_resp_data),
    .io_round(ctrl_io_round),
    .io_absorb(ctrl_io_absorb),
    .io_aindex(ctrl_io_aindex),
    .io_init(ctrl_io_init),
    .io_write(ctrl_io_write),
    .io_windex(ctrl_io_windex),
    .io_buffer_out(ctrl_io_buffer_out)
  );
  DpathModule dpath ( // @[sha3.scala 82:21:example.TestHarness.Sha3RocketConfig.fir@135936.4]
    .clock(dpath_clock),
    .reset(dpath_reset),
    .io_absorb(dpath_io_absorb),
    .io_init(dpath_io_init),
    .io_write(dpath_io_write),
    .io_round(dpath_io_round),
    .io_aindex(dpath_io_aindex),
    .io_message_in(dpath_io_message_in),
    .io_hash_out_0(dpath_io_hash_out_0),
    .io_hash_out_1(dpath_io_hash_out_1),
    .io_hash_out_2(dpath_io_hash_out_2),
    .io_hash_out_3(dpath_io_hash_out_3)
  );
  ExampleDCO dco (
    .col_sel_b(col_sel_b),
    .row_sel_b(row_sel_b),
    .code_regulator(code_regulator),
    .dither(dither),
    .sleep_b(sleep_b),
    .clock(dco_clock)
  );
  assign _T = ctrl_io_windex[1:0]; // @[:example.TestHarness.Sha3RocketConfig.fir@135941.4]
  assign _GEN_0 = dpath_io_hash_out_0; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign _GEN_1 = 2'h1 == _T ? dpath_io_hash_out_1 : _GEN_0; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign _GEN_2 = 2'h2 == _T ? dpath_io_hash_out_2 : _GEN_1; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign io_cmd_ready = ctrl_io_rocc_req_rdy; // @[sha3.scala 64:16:example.TestHarness.Sha3RocketConfig.fir@135921.4]
  assign io_mem_req_valid = ctrl_io_dmem_req_val; // @[sha3.scala 71:20:example.TestHarness.Sha3RocketConfig.fir@135927.4]
  assign io_mem_req_bits_addr = {{8'd0}, ctrl_io_dmem_req_addr}; // @[sha3.scala 74:24:example.TestHarness.Sha3RocketConfig.fir@135930.4]
  assign io_mem_req_bits_tag = {{1'd0}, ctrl_io_dmem_req_tag}; // @[sha3.scala 73:23:example.TestHarness.Sha3RocketConfig.fir@135929.4]
  assign io_mem_req_bits_cmd = ctrl_io_dmem_req_cmd; // @[sha3.scala 75:23:example.TestHarness.Sha3RocketConfig.fir@135931.4]
  assign io_mem_req_bits_data = 2'h3 == _T ? dpath_io_hash_out_3 : _GEN_2; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign io_busy = ctrl_io_busy; // @[sha3.scala 69:11:example.TestHarness.Sha3RocketConfig.fir@135926.4]
  assign ctrl_clock = clock; // @[:example.TestHarness.Sha3RocketConfig.fir@135918.4]
  assign ctrl_reset = reset; // @[:example.TestHarness.Sha3RocketConfig.fir@135919.4]
  assign ctrl_io_rocc_req_val = io_cmd_valid; // @[sha3.scala 63:26:example.TestHarness.Sha3RocketConfig.fir@135920.4]
  assign ctrl_io_rocc_funct = io_cmd_bits_inst_funct[1:0]; // @[sha3.scala 65:26:example.TestHarness.Sha3RocketConfig.fir@135922.4]
  assign ctrl_io_rocc_rs1 = io_cmd_bits_rs1; // @[sha3.scala 66:26:example.TestHarness.Sha3RocketConfig.fir@135923.4]
  assign ctrl_io_rocc_rs2 = io_cmd_bits_rs2; // @[sha3.scala 67:26:example.TestHarness.Sha3RocketConfig.fir@135924.4]
  assign ctrl_io_dmem_req_rdy = io_mem_req_ready; // @[sha3.scala 72:26:example.TestHarness.Sha3RocketConfig.fir@135928.4]
  assign ctrl_io_dmem_resp_val = io_mem_resp_valid; // @[sha3.scala 78:26:example.TestHarness.Sha3RocketConfig.fir@135933.4]
  assign ctrl_io_dmem_resp_tag = io_mem_resp_bits_tag[6:0]; // @[sha3.scala 79:26:example.TestHarness.Sha3RocketConfig.fir@135934.4]
  assign ctrl_io_dmem_resp_data = io_mem_resp_bits_data; // @[sha3.scala 80:26:example.TestHarness.Sha3RocketConfig.fir@135935.4]
  assign dpath_clock = clock; // @[:example.TestHarness.Sha3RocketConfig.fir@135938.4]
  assign dpath_reset = reset; // @[:example.TestHarness.Sha3RocketConfig.fir@135939.4]
  assign dpath_io_absorb = ctrl_io_absorb; // @[sha3.scala 88:19:example.TestHarness.Sha3RocketConfig.fir@135943.4]
  assign dpath_io_init = ctrl_io_init; // @[sha3.scala 89:17:example.TestHarness.Sha3RocketConfig.fir@135944.4]
  assign dpath_io_write = ctrl_io_write; // @[sha3.scala 90:18:example.TestHarness.Sha3RocketConfig.fir@135945.4]
  assign dpath_io_round = ctrl_io_round; // @[sha3.scala 91:18:example.TestHarness.Sha3RocketConfig.fir@135946.4]
  assign dpath_io_aindex = ctrl_io_aindex; // @[sha3.scala 93:19:example.TestHarness.Sha3RocketConfig.fir@135948.4]
  assign dpath_io_message_in = ctrl_io_buffer_out; // @[sha3.scala 84:23:example.TestHarness.Sha3RocketConfig.fir@135940.4]
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
