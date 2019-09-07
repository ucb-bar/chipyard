module CtrlModule( // @[:example.TestHarness.Sha3RocketConfig.fir@134590.2]
  input         clock, // @[:example.TestHarness.Sha3RocketConfig.fir@134591.4]
  input         reset, // @[:example.TestHarness.Sha3RocketConfig.fir@134592.4]
  input         io_rocc_req_val, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output        io_rocc_req_rdy, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input  [1:0]  io_rocc_funct, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input  [63:0] io_rocc_rs1, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input  [63:0] io_rocc_rs2, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output        io_busy, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output        io_dmem_req_val, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input         io_dmem_req_rdy, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [6:0]  io_dmem_req_tag, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [31:0] io_dmem_req_addr, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [4:0]  io_dmem_req_cmd, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input         io_dmem_resp_val, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input  [6:0]  io_dmem_resp_tag, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  input  [63:0] io_dmem_resp_data, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [4:0]  io_round, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output        io_absorb, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [4:0]  io_aindex, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output        io_init, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output        io_write, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [2:0]  io_windex, // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
  output [63:0] io_buffer_out // @[:example.TestHarness.Sha3RocketConfig.fir@134593.4]
);
  reg [63:0] msg_addr; // @[ctrl.scala 62:21:example.TestHarness.Sha3RocketConfig.fir@134598.4]
  reg [63:0] _RAND_0;
  reg [63:0] hash_addr; // @[ctrl.scala 63:21:example.TestHarness.Sha3RocketConfig.fir@134599.4]
  reg [63:0] _RAND_1;
  reg [63:0] msg_len; // @[ctrl.scala 64:21:example.TestHarness.Sha3RocketConfig.fir@134600.4]
  reg [63:0] _RAND_2;
  reg  busy; // @[ctrl.scala 66:17:example.TestHarness.Sha3RocketConfig.fir@134601.4]
  reg [31:0] _RAND_3;
  reg [6:0] dmem_resp_tag_reg; // @[ctrl.scala 77:30:example.TestHarness.Sha3RocketConfig.fir@134613.4]
  reg [31:0] _RAND_4;
  reg [2:0] mem_s; // @[ctrl.scala 81:18:example.TestHarness.Sha3RocketConfig.fir@134615.4]
  reg [31:0] _RAND_5;
  reg [63:0] buffer_0; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_6;
  reg [63:0] buffer_1; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_7;
  reg [63:0] buffer_2; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_8;
  reg [63:0] buffer_3; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_9;
  reg [63:0] buffer_4; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_10;
  reg [63:0] buffer_5; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_11;
  reg [63:0] buffer_6; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_12;
  reg [63:0] buffer_7; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_13;
  reg [63:0] buffer_8; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_14;
  reg [63:0] buffer_9; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_15;
  reg [63:0] buffer_10; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_16;
  reg [63:0] buffer_11; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_17;
  reg [63:0] buffer_12; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_18;
  reg [63:0] buffer_13; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_19;
  reg [63:0] buffer_14; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_20;
  reg [63:0] buffer_15; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_21;
  reg [63:0] buffer_16; // @[ctrl.scala 87:19:example.TestHarness.Sha3RocketConfig.fir@134636.4]
  reg [63:0] _RAND_22;
  reg  buffer_valid; // @[ctrl.scala 108:25:example.TestHarness.Sha3RocketConfig.fir@134648.4]
  reg [31:0] _RAND_23;
  reg [4:0] buffer_count; // @[ctrl.scala 109:25:example.TestHarness.Sha3RocketConfig.fir@134649.4]
  reg [31:0] _RAND_24;
  reg [31:0] read; // @[ctrl.scala 110:20:example.TestHarness.Sha3RocketConfig.fir@134650.4]
  reg [31:0] _RAND_25;
  reg [31:0] hashed; // @[ctrl.scala 111:20:example.TestHarness.Sha3RocketConfig.fir@134651.4]
  reg [31:0] _RAND_26;
  reg  areg; // @[ctrl.scala 112:20:example.TestHarness.Sha3RocketConfig.fir@134652.4]
  reg [31:0] _RAND_27;
  reg [4:0] mindex; // @[ctrl.scala 113:20:example.TestHarness.Sha3RocketConfig.fir@134653.4]
  reg [31:0] _RAND_28;
  reg [2:0] windex; // @[ctrl.scala 114:20:example.TestHarness.Sha3RocketConfig.fir@134654.4]
  reg [31:0] _RAND_29;
  reg [4:0] aindex; // @[ctrl.scala 115:20:example.TestHarness.Sha3RocketConfig.fir@134655.4]
  reg [31:0] _RAND_30;
  reg [4:0] pindex; // @[ctrl.scala 116:20:example.TestHarness.Sha3RocketConfig.fir@134656.4]
  reg [31:0] _RAND_31;
  reg  writes_done_0; // @[ctrl.scala 117:25:example.TestHarness.Sha3RocketConfig.fir@134663.4]
  reg [31:0] _RAND_32;
  reg  writes_done_1; // @[ctrl.scala 117:25:example.TestHarness.Sha3RocketConfig.fir@134663.4]
  reg [31:0] _RAND_33;
  reg  writes_done_2; // @[ctrl.scala 117:25:example.TestHarness.Sha3RocketConfig.fir@134663.4]
  reg [31:0] _RAND_34;
  reg  writes_done_3; // @[ctrl.scala 117:25:example.TestHarness.Sha3RocketConfig.fir@134663.4]
  reg [31:0] _RAND_35;
  reg  next_buff_val; // @[ctrl.scala 118:26:example.TestHarness.Sha3RocketConfig.fir@134664.4]
  reg [31:0] _RAND_36;
  wire  _T_2; // @[ctrl.scala 120:36:example.TestHarness.Sha3RocketConfig.fir@134665.4]
  wire  _T_3; // @[ctrl.scala 121:30:example.TestHarness.Sha3RocketConfig.fir@134666.4]
  wire  _T_4; // @[ctrl.scala 120:47:example.TestHarness.Sha3RocketConfig.fir@134667.4]
  reg [4:0] _T_5; // @[ctrl.scala 129:23:example.TestHarness.Sha3RocketConfig.fir@134669.4]
  reg [31:0] _RAND_37;
  wire [63:0] _GEN_1; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_2; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_3; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_4; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_5; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_6; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_7; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_8; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_9; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_10; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_11; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_12; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_13; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_14; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire [63:0] _GEN_15; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  wire  _T_6; // @[ctrl.scala 153:16:example.TestHarness.Sha3RocketConfig.fir@134677.4]
  wire [4:0] _T_8; // @[ctrl.scala 153:34:example.TestHarness.Sha3RocketConfig.fir@134679.4]
  wire [4:0] words_filled; // @[ctrl.scala 153:8:example.TestHarness.Sha3RocketConfig.fir@134680.4]
  wire [63:0] _GEN_0; // @[ctrl.scala 160:30:example.TestHarness.Sha3RocketConfig.fir@134681.4]
  wire [3:0] byte_offset; // @[ctrl.scala 160:30:example.TestHarness.Sha3RocketConfig.fir@134681.4]
  reg [2:0] state; // @[ctrl.scala 166:18:example.TestHarness.Sha3RocketConfig.fir@134682.4]
  reg [31:0] _RAND_38;
  reg [4:0] rindex; // @[ctrl.scala 169:19:example.TestHarness.Sha3RocketConfig.fir@134683.4]
  reg [31:0] _RAND_39;
  reg [4:0] rindex_reg; // @[ctrl.scala 185:23:example.TestHarness.Sha3RocketConfig.fir@134696.4]
  reg [31:0] _RAND_40;
  wire  _T_10; // @[ctrl.scala 190:24:example.TestHarness.Sha3RocketConfig.fir@134700.6]
  wire  _T_12; // @[ctrl.scala 191:26:example.TestHarness.Sha3RocketConfig.fir@134703.6]
  wire  _T_13; // @[ctrl.scala 192:26:example.TestHarness.Sha3RocketConfig.fir@134705.8]
  wire  _T_14; // @[ctrl.scala 198:32:example.TestHarness.Sha3RocketConfig.fir@134713.10]
  wire  _GEN_17; // @[ctrl.scala 198:45:example.TestHarness.Sha3RocketConfig.fir@134714.10]
  wire  _GEN_18; // @[ctrl.scala 198:45:example.TestHarness.Sha3RocketConfig.fir@134714.10]
  wire [63:0] _GEN_20; // @[ctrl.scala 198:45:example.TestHarness.Sha3RocketConfig.fir@134714.10]
  wire  _GEN_21; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  wire [63:0] _GEN_22; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  wire [63:0] _GEN_23; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  wire  _GEN_24; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  wire  _GEN_25; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  wire [63:0] _GEN_26; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  wire [63:0] _GEN_28; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  wire [63:0] _GEN_29; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  wire  _GEN_31; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  wire [63:0] _GEN_32; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  wire  _T_15; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134722.4]
  wire [63:0] _GEN_1006; // @[ctrl.scala 218:35:example.TestHarness.Sha3RocketConfig.fir@134724.6]
  wire  _T_16; // @[ctrl.scala 218:35:example.TestHarness.Sha3RocketConfig.fir@134724.6]
  wire  _T_17; // @[ctrl.scala 218:54:example.TestHarness.Sha3RocketConfig.fir@134725.6]
  wire  _T_18; // @[ctrl.scala 218:77:example.TestHarness.Sha3RocketConfig.fir@134726.6]
  wire  _T_19; // @[ctrl.scala 218:66:example.TestHarness.Sha3RocketConfig.fir@134727.6]
  wire  _T_20; // @[ctrl.scala 218:45:example.TestHarness.Sha3RocketConfig.fir@134728.6]
  wire  _T_21; // @[ctrl.scala 219:20:example.TestHarness.Sha3RocketConfig.fir@134729.6]
  wire  _T_22; // @[ctrl.scala 219:50:example.TestHarness.Sha3RocketConfig.fir@134730.6]
  wire  _T_23; // @[ctrl.scala 219:34:example.TestHarness.Sha3RocketConfig.fir@134731.6]
  wire  _T_24; // @[ctrl.scala 218:92:example.TestHarness.Sha3RocketConfig.fir@134732.6]
  wire  _T_25; // @[ctrl.scala 218:24:example.TestHarness.Sha3RocketConfig.fir@134733.6]
  wire [4:0] _GEN_39; // @[ctrl.scala 220:18:example.TestHarness.Sha3RocketConfig.fir@134734.6]
  wire  _T_26; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134744.6]
  wire  _T_27; // @[ctrl.scala 232:16:example.TestHarness.Sha3RocketConfig.fir@134746.8]
  wire  _T_29; // @[ctrl.scala 233:51:example.TestHarness.Sha3RocketConfig.fir@134749.10]
  wire  _T_30; // @[ctrl.scala 233:41:example.TestHarness.Sha3RocketConfig.fir@134750.10]
  wire [7:0] _T_31; // @[ctrl.scala 234:46:example.TestHarness.Sha3RocketConfig.fir@134752.10]
  wire [63:0] _GEN_1009; // @[ctrl.scala 234:36:example.TestHarness.Sha3RocketConfig.fir@134753.10]
  wire [63:0] _T_33; // @[ctrl.scala 234:36:example.TestHarness.Sha3RocketConfig.fir@134754.10]
  wire  _T_34; // @[ctrl.scala 239:28:example.TestHarness.Sha3RocketConfig.fir@134759.10]
  wire [4:0] _T_36; // @[ctrl.scala 240:26:example.TestHarness.Sha3RocketConfig.fir@134762.12]
  wire [31:0] _T_38; // @[ctrl.scala 241:22:example.TestHarness.Sha3RocketConfig.fir@134765.12]
  wire [31:0] _GEN_43; // @[ctrl.scala 239:47:example.TestHarness.Sha3RocketConfig.fir@134760.10]
  wire [31:0] _GEN_44; // @[ctrl.scala 251:32:example.TestHarness.Sha3RocketConfig.fir@134771.10]
  wire  _GEN_45; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  wire [63:0] _GEN_46; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  wire [4:0] _GEN_47; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  wire [31:0] _GEN_51; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  wire  _T_40; // @[ctrl.scala 261:19:example.TestHarness.Sha3RocketConfig.fir@134775.8]
  wire  _T_41; // @[ctrl.scala 263:22:example.TestHarness.Sha3RocketConfig.fir@134777.10]
  wire [63:0] _T_43; // @[ctrl.scala 266:24:example.TestHarness.Sha3RocketConfig.fir@134780.12]
  wire  _T_44; // @[ctrl.scala 266:34:example.TestHarness.Sha3RocketConfig.fir@134781.12]
  wire  _GEN_52; // @[ctrl.scala 266:41:example.TestHarness.Sha3RocketConfig.fir@134782.12]
  wire  _GEN_55; // @[ctrl.scala 263:29:example.TestHarness.Sha3RocketConfig.fir@134778.10]
  wire  _T_47; // @[ctrl.scala 280:14:example.TestHarness.Sha3RocketConfig.fir@134798.10]
  wire  _T_48; // @[ctrl.scala 279:46:example.TestHarness.Sha3RocketConfig.fir@134799.10]
  wire [63:0] _T_54; // @[ctrl.scala 288:32:example.TestHarness.Sha3RocketConfig.fir@134811.12]
  wire [63:0] _GEN_1012; // @[ctrl.scala 289:25:example.TestHarness.Sha3RocketConfig.fir@134815.12]
  wire  _T_57; // @[ctrl.scala 289:25:example.TestHarness.Sha3RocketConfig.fir@134815.12]
  wire  _GEN_58; // @[ctrl.scala 289:44:example.TestHarness.Sha3RocketConfig.fir@134816.12]
  wire [31:0] _GEN_63; // @[ctrl.scala 280:52:example.TestHarness.Sha3RocketConfig.fir@134800.10]
  wire [63:0] _GEN_64; // @[ctrl.scala 280:52:example.TestHarness.Sha3RocketConfig.fir@134800.10]
  wire  _GEN_65; // @[ctrl.scala 280:52:example.TestHarness.Sha3RocketConfig.fir@134800.10]
  wire  _GEN_67; // @[ctrl.scala 261:48:example.TestHarness.Sha3RocketConfig.fir@134776.8]
  wire [31:0] _GEN_71; // @[ctrl.scala 261:48:example.TestHarness.Sha3RocketConfig.fir@134776.8]
  wire [63:0] _GEN_72; // @[ctrl.scala 261:48:example.TestHarness.Sha3RocketConfig.fir@134776.8]
  wire  _T_58; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134828.8]
  wire [4:0] _T_62; // @[ctrl.scala 313:36:example.TestHarness.Sha3RocketConfig.fir@134835.12]
  wire  _GEN_96; // @[ctrl.scala 344:44:example.TestHarness.Sha3RocketConfig.fir@134872.16]
  wire  _GEN_101; // @[ctrl.scala 335:52:example.TestHarness.Sha3RocketConfig.fir@134862.14]
  wire  _GEN_103; // @[ctrl.scala 316:47:example.TestHarness.Sha3RocketConfig.fir@134838.12]
  wire [4:0] _GEN_124; // @[ctrl.scala 304:28:example.TestHarness.Sha3RocketConfig.fir@134830.10]
  wire  _GEN_125; // @[ctrl.scala 304:28:example.TestHarness.Sha3RocketConfig.fir@134830.10]
  wire [63:0] _GEN_128; // @[ctrl.scala 304:28:example.TestHarness.Sha3RocketConfig.fir@134830.10]
  wire  _T_77; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134886.10]
  wire  _T_78; // @[ctrl.scala 365:26:example.TestHarness.Sha3RocketConfig.fir@134889.12]
  wire  _T_79; // @[ctrl.scala 365:46:example.TestHarness.Sha3RocketConfig.fir@134890.12]
  wire  _T_80; // @[ctrl.scala 365:35:example.TestHarness.Sha3RocketConfig.fir@134891.12]
  wire  _T_81; // @[ctrl.scala 365:10:example.TestHarness.Sha3RocketConfig.fir@134892.12]
  wire  _T_82; // @[ctrl.scala 367:19:example.TestHarness.Sha3RocketConfig.fir@134894.14]
  wire  _T_83; // @[ctrl.scala 367:44:example.TestHarness.Sha3RocketConfig.fir@134895.14]
  wire  _T_84; // @[ctrl.scala 367:34:example.TestHarness.Sha3RocketConfig.fir@134896.14]
  wire  _T_85; // @[ctrl.scala 370:26:example.TestHarness.Sha3RocketConfig.fir@134898.16]
  wire [4:0] _T_87; // @[ctrl.scala 370:65:example.TestHarness.Sha3RocketConfig.fir@134900.16]
  wire  _T_88; // @[ctrl.scala 370:49:example.TestHarness.Sha3RocketConfig.fir@134901.16]
  wire  _T_89; // @[ctrl.scala 370:38:example.TestHarness.Sha3RocketConfig.fir@134902.16]
  wire  _T_90; // @[ctrl.scala 371:30:example.TestHarness.Sha3RocketConfig.fir@134903.16]
  wire  _T_91; // @[ctrl.scala 371:14:example.TestHarness.Sha3RocketConfig.fir@134904.16]
  wire [63:0] _GEN_129; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_130; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_131; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_132; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_133; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_134; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_135; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_136; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_137; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_138; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_139; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_140; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_141; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_142; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_143; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_144; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire [63:0] _GEN_145; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  wire  _T_93; // @[ctrl.scala 388:25:example.TestHarness.Sha3RocketConfig.fir@134914.16]
  wire  _T_94; // @[ctrl.scala 392:27:example.TestHarness.Sha3RocketConfig.fir@134916.18]
  wire  _T_95; // @[ctrl.scala 393:28:example.TestHarness.Sha3RocketConfig.fir@134918.20]
  wire [63:0] _GEN_181; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_182; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_183; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_184; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_185; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_186; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_187; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_188; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_189; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_190; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_191; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_192; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_193; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_194; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_195; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _GEN_196; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [55:0] _T_96; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  wire [63:0] _T_97; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134921.22]
  wire  _T_99; // @[ctrl.scala 410:29:example.TestHarness.Sha3RocketConfig.fir@134930.20]
  wire [63:0] _T_102; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134937.22]
  wire  _T_103; // @[ctrl.scala 430:25:example.TestHarness.Sha3RocketConfig.fir@134943.18]
  wire  _T_104; // @[ctrl.scala 432:26:example.TestHarness.Sha3RocketConfig.fir@134945.20]
  wire  _T_105; // @[ctrl.scala 434:28:example.TestHarness.Sha3RocketConfig.fir@134947.22]
  wire [7:0] _T_106; // @[ctrl.scala 444:61:example.TestHarness.Sha3RocketConfig.fir@134949.24]
  wire [8:0] _T_107; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134950.24]
  wire [63:0] _buffer_pindex_7; // @[ctrl.scala 444:30:example.TestHarness.Sha3RocketConfig.fir@134951.24 ctrl.scala 444:30:example.TestHarness.Sha3RocketConfig.fir@134951.24]
  wire  _T_108; // @[ctrl.scala 446:34:example.TestHarness.Sha3RocketConfig.fir@134954.24]
  wire [15:0] _T_109; // @[ctrl.scala 454:61:example.TestHarness.Sha3RocketConfig.fir@134956.26]
  wire [16:0] _T_110; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134957.26]
  wire [63:0] _buffer_pindex_9; // @[ctrl.scala 454:30:example.TestHarness.Sha3RocketConfig.fir@134958.26 ctrl.scala 454:30:example.TestHarness.Sha3RocketConfig.fir@134958.26]
  wire  _T_111; // @[ctrl.scala 456:34:example.TestHarness.Sha3RocketConfig.fir@134961.26]
  wire [23:0] _T_112; // @[ctrl.scala 464:61:example.TestHarness.Sha3RocketConfig.fir@134963.28]
  wire [24:0] _T_113; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134964.28]
  wire [63:0] _buffer_pindex_11; // @[ctrl.scala 464:30:example.TestHarness.Sha3RocketConfig.fir@134965.28 ctrl.scala 464:30:example.TestHarness.Sha3RocketConfig.fir@134965.28]
  wire  _T_114; // @[ctrl.scala 466:34:example.TestHarness.Sha3RocketConfig.fir@134968.28]
  wire [31:0] _T_115; // @[ctrl.scala 474:61:example.TestHarness.Sha3RocketConfig.fir@134970.30]
  wire [32:0] _T_116; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134971.30]
  wire [63:0] _buffer_pindex_13; // @[ctrl.scala 474:30:example.TestHarness.Sha3RocketConfig.fir@134972.30 ctrl.scala 474:30:example.TestHarness.Sha3RocketConfig.fir@134972.30]
  wire  _T_117; // @[ctrl.scala 476:34:example.TestHarness.Sha3RocketConfig.fir@134975.30]
  wire [39:0] _T_118; // @[ctrl.scala 484:61:example.TestHarness.Sha3RocketConfig.fir@134977.32]
  wire [40:0] _T_119; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134978.32]
  wire [63:0] _buffer_pindex_15; // @[ctrl.scala 484:30:example.TestHarness.Sha3RocketConfig.fir@134979.32 ctrl.scala 484:30:example.TestHarness.Sha3RocketConfig.fir@134979.32]
  wire  _T_120; // @[ctrl.scala 486:34:example.TestHarness.Sha3RocketConfig.fir@134982.32]
  wire [47:0] _T_121; // @[ctrl.scala 494:61:example.TestHarness.Sha3RocketConfig.fir@134984.34]
  wire [48:0] _T_122; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134985.34]
  wire [63:0] _buffer_pindex_17; // @[ctrl.scala 494:30:example.TestHarness.Sha3RocketConfig.fir@134986.34 ctrl.scala 494:30:example.TestHarness.Sha3RocketConfig.fir@134986.34]
  wire [56:0] _T_125; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134992.36]
  wire [63:0] _buffer_pindex_19; // @[ctrl.scala 504:30:example.TestHarness.Sha3RocketConfig.fir@134993.36 ctrl.scala 504:30:example.TestHarness.Sha3RocketConfig.fir@134993.36]
  wire  _T_126; // @[ctrl.scala 509:29:example.TestHarness.Sha3RocketConfig.fir@134997.22]
  wire  _T_128; // @[ctrl.scala 509:41:example.TestHarness.Sha3RocketConfig.fir@134999.22]
  wire [4:0] _GEN_656; // @[ctrl.scala 526:17:example.TestHarness.Sha3RocketConfig.fir@135009.14]
  wire [4:0] _T_134; // @[ctrl.scala 554:28:example.TestHarness.Sha3RocketConfig.fir@135031.16]
  wire [4:0] _GEN_663; // @[ctrl.scala 523:24:example.TestHarness.Sha3RocketConfig.fir@135007.12]
  wire  _T_135; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135038.12]
  wire  _T_136; // @[ctrl.scala 563:17:example.TestHarness.Sha3RocketConfig.fir@135041.14]
  wire  _GEN_668; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135039.12]
  wire  _GEN_670; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134887.10]
  wire [4:0] _GEN_689; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134887.10]
  wire [4:0] _GEN_710; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134829.8]
  wire  _GEN_711; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134829.8]
  wire [63:0] _GEN_714; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134829.8]
  wire  _GEN_717; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire [63:0] _GEN_718; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire [4:0] _GEN_719; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire [31:0] _GEN_723; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire  _GEN_724; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire [63:0] _GEN_727; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire [4:0] _GEN_745; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  wire [4:0] _GEN_747; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire  _GEN_750; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire [63:0] _GEN_751; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire [4:0] _GEN_752; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire [31:0] _GEN_755; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire  _GEN_756; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire [63:0] _GEN_758; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  wire [4:0] _T_137; // @[ctrl.scala 571:28:example.TestHarness.Sha3RocketConfig.fir@135047.6]
  wire  _T_138; // @[ctrl.scala 571:34:example.TestHarness.Sha3RocketConfig.fir@135048.6]
  wire [4:0] _GEN_811; // @[ctrl.scala 571:59:example.TestHarness.Sha3RocketConfig.fir@135049.6]
  wire [4:0] _GEN_829; // @[ctrl.scala 570:28:example.TestHarness.Sha3RocketConfig.fir@135046.4]
  wire  _T_143; // @[ctrl.scala 584:18:example.TestHarness.Sha3RocketConfig.fir@135058.4]
  wire  _T_144; // @[ctrl.scala 583:35:example.TestHarness.Sha3RocketConfig.fir@135059.4]
  wire  _T_145; // @[ctrl.scala 586:17:example.TestHarness.Sha3RocketConfig.fir@135061.6]
  wire  _GEN_830; // @[ctrl.scala 586:27:example.TestHarness.Sha3RocketConfig.fir@135062.6]
  wire  _GEN_831; // @[ctrl.scala 584:45:example.TestHarness.Sha3RocketConfig.fir@135060.4]
  wire  _T_146; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135068.4]
  wire  _T_147; // @[ctrl.scala 599:39:example.TestHarness.Sha3RocketConfig.fir@135070.6]
  wire  _T_148; // @[ctrl.scala 599:55:example.TestHarness.Sha3RocketConfig.fir@135071.6]
  wire [63:0] _GEN_1017; // @[ctrl.scala 599:81:example.TestHarness.Sha3RocketConfig.fir@135072.6]
  wire  _T_149; // @[ctrl.scala 599:81:example.TestHarness.Sha3RocketConfig.fir@135072.6]
  wire  _T_150; // @[ctrl.scala 599:71:example.TestHarness.Sha3RocketConfig.fir@135073.6]
  wire  _T_151; // @[ctrl.scala 599:24:example.TestHarness.Sha3RocketConfig.fir@135074.6]
  wire  _GEN_832; // @[ctrl.scala 600:20:example.TestHarness.Sha3RocketConfig.fir@135075.6]
  wire  _T_152; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135084.6]
  wire  _T_153; // @[ctrl.scala 608:17:example.TestHarness.Sha3RocketConfig.fir@135086.8]
  wire [4:0] _T_155; // @[ctrl.scala 610:22:example.TestHarness.Sha3RocketConfig.fir@135090.8]
  wire  _T_156; // @[ctrl.scala 611:20:example.TestHarness.Sha3RocketConfig.fir@135092.8]
  wire [31:0] _T_158; // @[ctrl.scala 619:24:example.TestHarness.Sha3RocketConfig.fir@135100.10]
  wire  _T_159; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135109.8]
  wire  _T_160; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135115.10]
  wire  _T_161; // @[ctrl.scala 631:17:example.TestHarness.Sha3RocketConfig.fir@135117.12]
  wire [4:0] _T_166; // @[ctrl.scala 640:24:example.TestHarness.Sha3RocketConfig.fir@135132.16]
  wire  _T_167; // @[ctrl.scala 647:19:example.TestHarness.Sha3RocketConfig.fir@135141.14]
  wire  _T_168; // @[ctrl.scala 647:40:example.TestHarness.Sha3RocketConfig.fir@135142.14]
  wire  _T_169; // @[ctrl.scala 647:62:example.TestHarness.Sha3RocketConfig.fir@135143.14]
  wire  _T_170; // @[ctrl.scala 647:52:example.TestHarness.Sha3RocketConfig.fir@135144.14]
  wire  _T_171; // @[ctrl.scala 647:29:example.TestHarness.Sha3RocketConfig.fir@135145.14]
  wire  _GEN_852; // @[ctrl.scala 631:32:example.TestHarness.Sha3RocketConfig.fir@135118.12]
  wire  _T_172; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135156.12]
  wire  _T_173; // @[ctrl.scala 658:31:example.TestHarness.Sha3RocketConfig.fir@135158.14]
  wire [5:0] _T_174; // @[ctrl.scala 659:45:example.TestHarness.Sha3RocketConfig.fir@135160.14]
  wire [63:0] _GEN_1020; // @[ctrl.scala 659:35:example.TestHarness.Sha3RocketConfig.fir@135161.14]
  wire [63:0] _T_176; // @[ctrl.scala 659:35:example.TestHarness.Sha3RocketConfig.fir@135162.14]
  wire [4:0] _GEN_1021; // @[ctrl.scala 660:47:example.TestHarness.Sha3RocketConfig.fir@135164.14]
  wire [4:0] _T_178; // @[ctrl.scala 660:47:example.TestHarness.Sha3RocketConfig.fir@135165.14]
  wire [2:0] _T_180; // @[ctrl.scala 664:24:example.TestHarness.Sha3RocketConfig.fir@135170.16]
  wire [4:0] _T_181; // @[ctrl.scala 670:29:example.TestHarness.Sha3RocketConfig.fir@135174.16]
  wire  _T_182; // @[ctrl.scala 670:35:example.TestHarness.Sha3RocketConfig.fir@135175.16]
  wire [4:0] _T_185; // @[ctrl.scala 672:43:example.TestHarness.Sha3RocketConfig.fir@135179.18]
  wire [1:0] _T_186; // @[:example.TestHarness.Sha3RocketConfig.fir@135180.18]
  wire  _GEN_1022; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_857; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_1023; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_858; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_1024; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_859; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_1025; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _GEN_860; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  wire  _T_187; // @[ctrl.scala 675:30:example.TestHarness.Sha3RocketConfig.fir@135184.14]
  wire  _T_188; // @[ctrl.scala 675:30:example.TestHarness.Sha3RocketConfig.fir@135185.14]
  wire  _T_189; // @[ctrl.scala 675:30:example.TestHarness.Sha3RocketConfig.fir@135186.14]
  wire  _GEN_885; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  wire [63:0] _GEN_886; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  wire [4:0] _GEN_887; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  wire  _GEN_903; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  wire  _GEN_908; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  wire  _GEN_912; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  wire [63:0] _GEN_913; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  wire [4:0] _GEN_914; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  wire  _GEN_915; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  wire  _GEN_928; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  wire  _GEN_934; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  wire  _GEN_937; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  wire [63:0] _GEN_938; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  wire [4:0] _GEN_939; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  wire  _GEN_940; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  wire  _GEN_953; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  wire  _GEN_954; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  wire  _GEN_966; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  wire [63:0] _GEN_967; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  wire [4:0] _GEN_968; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  wire  _GEN_969; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  wire  _GEN_979; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  wire [63:0] _GEN_994; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135069.4]
  wire [4:0] _GEN_995; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135069.4]
  wire  _GEN_996; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135069.4]
  assign _T_2 = buffer_count >= mindex; // @[ctrl.scala 120:36:example.TestHarness.Sha3RocketConfig.fir@134665.4]
  assign _T_3 = pindex >= 5'h10; // @[ctrl.scala 121:30:example.TestHarness.Sha3RocketConfig.fir@134666.4]
  assign _T_4 = _T_2 & _T_3; // @[ctrl.scala 120:47:example.TestHarness.Sha3RocketConfig.fir@134667.4]
  assign _GEN_1 = 5'h1 == io_aindex ? buffer_1 : buffer_0; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_2 = 5'h2 == io_aindex ? buffer_2 : _GEN_1; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_3 = 5'h3 == io_aindex ? buffer_3 : _GEN_2; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_4 = 5'h4 == io_aindex ? buffer_4 : _GEN_3; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_5 = 5'h5 == io_aindex ? buffer_5 : _GEN_4; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_6 = 5'h6 == io_aindex ? buffer_6 : _GEN_5; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_7 = 5'h7 == io_aindex ? buffer_7 : _GEN_6; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_8 = 5'h8 == io_aindex ? buffer_8 : _GEN_7; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_9 = 5'h9 == io_aindex ? buffer_9 : _GEN_8; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_10 = 5'ha == io_aindex ? buffer_10 : _GEN_9; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_11 = 5'hb == io_aindex ? buffer_11 : _GEN_10; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_12 = 5'hc == io_aindex ? buffer_12 : _GEN_11; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_13 = 5'hd == io_aindex ? buffer_13 : _GEN_12; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_14 = 5'he == io_aindex ? buffer_14 : _GEN_13; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _GEN_15 = 5'hf == io_aindex ? buffer_15 : _GEN_14; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
  assign _T_6 = mindex > 5'h0; // @[ctrl.scala 153:16:example.TestHarness.Sha3RocketConfig.fir@134677.4]
  assign _T_8 = mindex - 5'h1; // @[ctrl.scala 153:34:example.TestHarness.Sha3RocketConfig.fir@134679.4]
  assign words_filled = _T_6 ? _T_8 : mindex; // @[ctrl.scala 153:8:example.TestHarness.Sha3RocketConfig.fir@134680.4]
  assign _GEN_0 = msg_len % 64'h8; // @[ctrl.scala 160:30:example.TestHarness.Sha3RocketConfig.fir@134681.4]
  assign byte_offset = _GEN_0[3:0]; // @[ctrl.scala 160:30:example.TestHarness.Sha3RocketConfig.fir@134681.4]
  assign _T_10 = busy == 1'h0; // @[ctrl.scala 190:24:example.TestHarness.Sha3RocketConfig.fir@134700.6]
  assign _T_12 = io_rocc_req_val & _T_10; // @[ctrl.scala 191:26:example.TestHarness.Sha3RocketConfig.fir@134703.6]
  assign _T_13 = io_rocc_funct == 2'h0; // @[ctrl.scala 192:26:example.TestHarness.Sha3RocketConfig.fir@134705.8]
  assign _T_14 = io_rocc_funct == 2'h1; // @[ctrl.scala 198:32:example.TestHarness.Sha3RocketConfig.fir@134713.10]
  assign _GEN_17 = _T_14 | busy; // @[ctrl.scala 198:45:example.TestHarness.Sha3RocketConfig.fir@134714.10]
  assign _GEN_18 = _T_14 | _T_10; // @[ctrl.scala 198:45:example.TestHarness.Sha3RocketConfig.fir@134714.10]
  assign _GEN_20 = _T_14 ? io_rocc_rs1 : msg_len; // @[ctrl.scala 198:45:example.TestHarness.Sha3RocketConfig.fir@134714.10]
  assign _GEN_21 = _T_13 | _GEN_18; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  assign _GEN_22 = _T_13 ? io_rocc_rs1 : msg_addr; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  assign _GEN_23 = _T_13 ? io_rocc_rs2 : hash_addr; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  assign _GEN_24 = _T_13 | _GEN_17; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  assign _GEN_25 = _T_13 ? busy : _GEN_17; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  assign _GEN_26 = _T_13 ? msg_len : _GEN_20; // @[ctrl.scala 192:38:example.TestHarness.Sha3RocketConfig.fir@134706.8]
  assign _GEN_28 = _T_12 ? _GEN_22 : msg_addr; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  assign _GEN_29 = _T_12 ? _GEN_23 : hash_addr; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  assign _GEN_31 = _T_12 ? _GEN_25 : busy; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  assign _GEN_32 = _T_12 ? _GEN_26 : msg_len; // @[ctrl.scala 191:35:example.TestHarness.Sha3RocketConfig.fir@134704.6]
  assign _T_15 = 3'h0 == mem_s; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134722.4]
  assign _GEN_1006 = {{32'd0}, read}; // @[ctrl.scala 218:35:example.TestHarness.Sha3RocketConfig.fir@134724.6]
  assign _T_16 = _GEN_1006 < msg_len; // @[ctrl.scala 218:35:example.TestHarness.Sha3RocketConfig.fir@134724.6]
  assign _T_17 = _GEN_1006 == msg_len; // @[ctrl.scala 218:54:example.TestHarness.Sha3RocketConfig.fir@134725.6]
  assign _T_18 = msg_len == 64'h0; // @[ctrl.scala 218:77:example.TestHarness.Sha3RocketConfig.fir@134726.6]
  assign _T_19 = _T_17 & _T_18; // @[ctrl.scala 218:66:example.TestHarness.Sha3RocketConfig.fir@134727.6]
  assign _T_20 = _T_16 | _T_19; // @[ctrl.scala 218:45:example.TestHarness.Sha3RocketConfig.fir@134728.6]
  assign _T_21 = buffer_valid == 1'h0; // @[ctrl.scala 219:20:example.TestHarness.Sha3RocketConfig.fir@134729.6]
  assign _T_22 = buffer_count == 5'h0; // @[ctrl.scala 219:50:example.TestHarness.Sha3RocketConfig.fir@134730.6]
  assign _T_23 = _T_21 & _T_22; // @[ctrl.scala 219:34:example.TestHarness.Sha3RocketConfig.fir@134731.6]
  assign _T_24 = _T_20 & _T_23; // @[ctrl.scala 218:92:example.TestHarness.Sha3RocketConfig.fir@134732.6]
  assign _T_25 = busy & _T_24; // @[ctrl.scala 218:24:example.TestHarness.Sha3RocketConfig.fir@134733.6]
  assign _GEN_39 = _T_25 ? 5'h0 : buffer_count; // @[ctrl.scala 220:18:example.TestHarness.Sha3RocketConfig.fir@134734.6]
  assign _T_26 = 3'h1 == mem_s; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134744.6]
  assign _T_27 = state != 3'h4; // @[ctrl.scala 232:16:example.TestHarness.Sha3RocketConfig.fir@134746.8]
  assign _T_29 = mindex < 5'h11; // @[ctrl.scala 233:51:example.TestHarness.Sha3RocketConfig.fir@134749.10]
  assign _T_30 = _T_16 & _T_29; // @[ctrl.scala 233:41:example.TestHarness.Sha3RocketConfig.fir@134750.10]
  assign _T_31 = {mindex, 3'h0}; // @[ctrl.scala 234:46:example.TestHarness.Sha3RocketConfig.fir@134752.10]
  assign _GEN_1009 = {{56'd0}, _T_31}; // @[ctrl.scala 234:36:example.TestHarness.Sha3RocketConfig.fir@134753.10]
  assign _T_33 = msg_addr + _GEN_1009; // @[ctrl.scala 234:36:example.TestHarness.Sha3RocketConfig.fir@134754.10]
  assign _T_34 = io_dmem_req_rdy & io_dmem_req_val; // @[ctrl.scala 239:28:example.TestHarness.Sha3RocketConfig.fir@134759.10]
  assign _T_36 = mindex + 5'h1; // @[ctrl.scala 240:26:example.TestHarness.Sha3RocketConfig.fir@134762.12]
  assign _T_38 = read + 32'h8; // @[ctrl.scala 241:22:example.TestHarness.Sha3RocketConfig.fir@134765.12]
  assign _GEN_43 = _T_34 ? _T_38 : read; // @[ctrl.scala 239:47:example.TestHarness.Sha3RocketConfig.fir@134760.10]
  assign _GEN_44 = _T_18 ? 32'h1 : _GEN_43; // @[ctrl.scala 251:32:example.TestHarness.Sha3RocketConfig.fir@134771.10]
  assign _GEN_45 = _T_27 & _T_30; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  assign _GEN_46 = _T_27 ? _T_33 : 64'h0; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  assign _GEN_47 = _T_27 ? mindex : rindex; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  assign _GEN_51 = _T_27 ? _GEN_44 : read; // @[ctrl.scala 232:28:example.TestHarness.Sha3RocketConfig.fir@134747.8]
  assign _T_40 = mindex < 5'h10; // @[ctrl.scala 261:19:example.TestHarness.Sha3RocketConfig.fir@134775.8]
  assign _T_41 = msg_len > _GEN_1006; // @[ctrl.scala 263:22:example.TestHarness.Sha3RocketConfig.fir@134777.10]
  assign _T_43 = msg_len + 64'h8; // @[ctrl.scala 266:24:example.TestHarness.Sha3RocketConfig.fir@134780.12]
  assign _T_44 = _T_43 < _GEN_1006; // @[ctrl.scala 266:34:example.TestHarness.Sha3RocketConfig.fir@134781.12]
  assign _GEN_52 = _T_44 ? 1'h0 : buffer_valid; // @[ctrl.scala 266:41:example.TestHarness.Sha3RocketConfig.fir@134782.12]
  assign _GEN_55 = _T_41 & _GEN_52; // @[ctrl.scala 263:29:example.TestHarness.Sha3RocketConfig.fir@134778.10]
  assign _T_47 = _T_34 == 1'h0; // @[ctrl.scala 280:14:example.TestHarness.Sha3RocketConfig.fir@134798.10]
  assign _T_48 = _T_29 & _T_47; // @[ctrl.scala 279:46:example.TestHarness.Sha3RocketConfig.fir@134799.10]
  assign _T_54 = msg_addr + 64'h88; // @[ctrl.scala 288:32:example.TestHarness.Sha3RocketConfig.fir@134811.12]
  assign _GEN_1012 = {{32'd0}, _T_38}; // @[ctrl.scala 289:25:example.TestHarness.Sha3RocketConfig.fir@134815.12]
  assign _T_57 = msg_len < _GEN_1012; // @[ctrl.scala 289:25:example.TestHarness.Sha3RocketConfig.fir@134815.12]
  assign _GEN_58 = _T_57 ? 1'h0 : buffer_valid; // @[ctrl.scala 289:44:example.TestHarness.Sha3RocketConfig.fir@134816.12]
  assign _GEN_63 = _T_48 ? _GEN_51 : _T_38; // @[ctrl.scala 280:52:example.TestHarness.Sha3RocketConfig.fir@134800.10]
  assign _GEN_64 = _T_48 ? _GEN_28 : _T_54; // @[ctrl.scala 280:52:example.TestHarness.Sha3RocketConfig.fir@134800.10]
  assign _GEN_65 = _T_48 ? buffer_valid : _GEN_58; // @[ctrl.scala 280:52:example.TestHarness.Sha3RocketConfig.fir@134800.10]
  assign _GEN_67 = _T_40 ? _GEN_55 : _GEN_65; // @[ctrl.scala 261:48:example.TestHarness.Sha3RocketConfig.fir@134776.8]
  assign _GEN_71 = _T_40 ? _GEN_51 : _GEN_63; // @[ctrl.scala 261:48:example.TestHarness.Sha3RocketConfig.fir@134776.8]
  assign _GEN_72 = _T_40 ? _GEN_28 : _GEN_64; // @[ctrl.scala 261:48:example.TestHarness.Sha3RocketConfig.fir@134776.8]
  assign _T_58 = 3'h2 == mem_s; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134828.8]
  assign _T_62 = buffer_count + 5'h1; // @[ctrl.scala 313:36:example.TestHarness.Sha3RocketConfig.fir@134835.12]
  assign _GEN_96 = _T_57 ? 1'h0 : 1'h1; // @[ctrl.scala 344:44:example.TestHarness.Sha3RocketConfig.fir@134872.16]
  assign _GEN_101 = _T_48 ? buffer_valid : _GEN_96; // @[ctrl.scala 335:52:example.TestHarness.Sha3RocketConfig.fir@134862.14]
  assign _GEN_103 = _T_40 ? _GEN_55 : _GEN_101; // @[ctrl.scala 316:47:example.TestHarness.Sha3RocketConfig.fir@134838.12]
  assign _GEN_124 = io_dmem_resp_val ? _T_62 : buffer_count; // @[ctrl.scala 304:28:example.TestHarness.Sha3RocketConfig.fir@134830.10]
  assign _GEN_125 = io_dmem_resp_val ? _GEN_103 : buffer_valid; // @[ctrl.scala 304:28:example.TestHarness.Sha3RocketConfig.fir@134830.10]
  assign _GEN_128 = io_dmem_resp_val ? _GEN_72 : _GEN_28; // @[ctrl.scala 304:28:example.TestHarness.Sha3RocketConfig.fir@134830.10]
  assign _T_77 = 3'h3 == mem_s; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@134886.10]
  assign _T_78 = buffer_count < mindex; // @[ctrl.scala 365:26:example.TestHarness.Sha3RocketConfig.fir@134889.12]
  assign _T_79 = pindex >= buffer_count; // @[ctrl.scala 365:46:example.TestHarness.Sha3RocketConfig.fir@134890.12]
  assign _T_80 = _T_78 & _T_79; // @[ctrl.scala 365:35:example.TestHarness.Sha3RocketConfig.fir@134891.12]
  assign _T_81 = _T_80 == 1'h0; // @[ctrl.scala 365:10:example.TestHarness.Sha3RocketConfig.fir@134892.12]
  assign _T_82 = pindex > words_filled; // @[ctrl.scala 367:19:example.TestHarness.Sha3RocketConfig.fir@134894.14]
  assign _T_83 = pindex < 5'h10; // @[ctrl.scala 367:44:example.TestHarness.Sha3RocketConfig.fir@134895.14]
  assign _T_84 = _T_82 & _T_83; // @[ctrl.scala 367:34:example.TestHarness.Sha3RocketConfig.fir@134896.14]
  assign _T_85 = byte_offset == 4'h0; // @[ctrl.scala 370:26:example.TestHarness.Sha3RocketConfig.fir@134898.16]
  assign _T_87 = words_filled + 5'h1; // @[ctrl.scala 370:65:example.TestHarness.Sha3RocketConfig.fir@134900.16]
  assign _T_88 = pindex == _T_87; // @[ctrl.scala 370:49:example.TestHarness.Sha3RocketConfig.fir@134901.16]
  assign _T_89 = _T_85 & _T_88; // @[ctrl.scala 370:38:example.TestHarness.Sha3RocketConfig.fir@134902.16]
  assign _T_90 = words_filled > 5'h0; // @[ctrl.scala 371:30:example.TestHarness.Sha3RocketConfig.fir@134903.16]
  assign _T_91 = _T_89 & _T_90; // @[ctrl.scala 371:14:example.TestHarness.Sha3RocketConfig.fir@134904.16]
  assign _GEN_129 = 5'h0 == pindex ? 64'h1 : buffer_0; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_130 = 5'h1 == pindex ? 64'h1 : buffer_1; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_131 = 5'h2 == pindex ? 64'h1 : buffer_2; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_132 = 5'h3 == pindex ? 64'h1 : buffer_3; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_133 = 5'h4 == pindex ? 64'h1 : buffer_4; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_134 = 5'h5 == pindex ? 64'h1 : buffer_5; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_135 = 5'h6 == pindex ? 64'h1 : buffer_6; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_136 = 5'h7 == pindex ? 64'h1 : buffer_7; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_137 = 5'h8 == pindex ? 64'h1 : buffer_8; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_138 = 5'h9 == pindex ? 64'h1 : buffer_9; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_139 = 5'ha == pindex ? 64'h1 : buffer_10; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_140 = 5'hb == pindex ? 64'h1 : buffer_11; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_141 = 5'hc == pindex ? 64'h1 : buffer_12; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_142 = 5'hd == pindex ? 64'h1 : buffer_13; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_143 = 5'he == pindex ? 64'h1 : buffer_14; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_144 = 5'hf == pindex ? 64'h1 : buffer_15; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _GEN_145 = 5'h10 == pindex ? 64'h1 : buffer_16; // @[ctrl.scala 377:28:example.TestHarness.Sha3RocketConfig.fir@134907.18]
  assign _T_93 = pindex == 5'h10; // @[ctrl.scala 388:25:example.TestHarness.Sha3RocketConfig.fir@134914.16]
  assign _T_94 = words_filled == 5'h10; // @[ctrl.scala 392:27:example.TestHarness.Sha3RocketConfig.fir@134916.18]
  assign _T_95 = byte_offset == 4'h7; // @[ctrl.scala 393:28:example.TestHarness.Sha3RocketConfig.fir@134918.20]
  assign _GEN_181 = 5'h1 == pindex ? buffer_1 : buffer_0; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_182 = 5'h2 == pindex ? buffer_2 : _GEN_181; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_183 = 5'h3 == pindex ? buffer_3 : _GEN_182; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_184 = 5'h4 == pindex ? buffer_4 : _GEN_183; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_185 = 5'h5 == pindex ? buffer_5 : _GEN_184; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_186 = 5'h6 == pindex ? buffer_6 : _GEN_185; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_187 = 5'h7 == pindex ? buffer_7 : _GEN_186; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_188 = 5'h8 == pindex ? buffer_8 : _GEN_187; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_189 = 5'h9 == pindex ? buffer_9 : _GEN_188; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_190 = 5'ha == pindex ? buffer_10 : _GEN_189; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_191 = 5'hb == pindex ? buffer_11 : _GEN_190; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_192 = 5'hc == pindex ? buffer_12 : _GEN_191; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_193 = 5'hd == pindex ? buffer_13 : _GEN_192; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_194 = 5'he == pindex ? buffer_14 : _GEN_193; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_195 = 5'hf == pindex ? buffer_15 : _GEN_194; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _GEN_196 = 5'h10 == pindex ? buffer_16 : _GEN_195; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _T_96 = _GEN_196[55:0]; // @[ctrl.scala 402:61:example.TestHarness.Sha3RocketConfig.fir@134920.22]
  assign _T_97 = {8'h81,_T_96}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134921.22]
  assign _T_99 = words_filled < 5'h10; // @[ctrl.scala 410:29:example.TestHarness.Sha3RocketConfig.fir@134930.20]
  assign _T_102 = {8'h80,_T_96}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134937.22]
  assign _T_103 = pindex == words_filled; // @[ctrl.scala 430:25:example.TestHarness.Sha3RocketConfig.fir@134943.18]
  assign _T_104 = byte_offset != 4'h0; // @[ctrl.scala 432:26:example.TestHarness.Sha3RocketConfig.fir@134945.20]
  assign _T_105 = byte_offset == 4'h1; // @[ctrl.scala 434:28:example.TestHarness.Sha3RocketConfig.fir@134947.22]
  assign _T_106 = _GEN_196[7:0]; // @[ctrl.scala 444:61:example.TestHarness.Sha3RocketConfig.fir@134949.24]
  assign _T_107 = {1'h1,_T_106}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134950.24]
  assign _buffer_pindex_7 = {{55'd0}, _T_107}; // @[ctrl.scala 444:30:example.TestHarness.Sha3RocketConfig.fir@134951.24 ctrl.scala 444:30:example.TestHarness.Sha3RocketConfig.fir@134951.24]
  assign _T_108 = byte_offset == 4'h2; // @[ctrl.scala 446:34:example.TestHarness.Sha3RocketConfig.fir@134954.24]
  assign _T_109 = _GEN_196[15:0]; // @[ctrl.scala 454:61:example.TestHarness.Sha3RocketConfig.fir@134956.26]
  assign _T_110 = {1'h1,_T_109}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134957.26]
  assign _buffer_pindex_9 = {{47'd0}, _T_110}; // @[ctrl.scala 454:30:example.TestHarness.Sha3RocketConfig.fir@134958.26 ctrl.scala 454:30:example.TestHarness.Sha3RocketConfig.fir@134958.26]
  assign _T_111 = byte_offset == 4'h3; // @[ctrl.scala 456:34:example.TestHarness.Sha3RocketConfig.fir@134961.26]
  assign _T_112 = _GEN_196[23:0]; // @[ctrl.scala 464:61:example.TestHarness.Sha3RocketConfig.fir@134963.28]
  assign _T_113 = {1'h1,_T_112}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134964.28]
  assign _buffer_pindex_11 = {{39'd0}, _T_113}; // @[ctrl.scala 464:30:example.TestHarness.Sha3RocketConfig.fir@134965.28 ctrl.scala 464:30:example.TestHarness.Sha3RocketConfig.fir@134965.28]
  assign _T_114 = byte_offset == 4'h4; // @[ctrl.scala 466:34:example.TestHarness.Sha3RocketConfig.fir@134968.28]
  assign _T_115 = _GEN_196[31:0]; // @[ctrl.scala 474:61:example.TestHarness.Sha3RocketConfig.fir@134970.30]
  assign _T_116 = {1'h1,_T_115}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134971.30]
  assign _buffer_pindex_13 = {{31'd0}, _T_116}; // @[ctrl.scala 474:30:example.TestHarness.Sha3RocketConfig.fir@134972.30 ctrl.scala 474:30:example.TestHarness.Sha3RocketConfig.fir@134972.30]
  assign _T_117 = byte_offset == 4'h5; // @[ctrl.scala 476:34:example.TestHarness.Sha3RocketConfig.fir@134975.30]
  assign _T_118 = _GEN_196[39:0]; // @[ctrl.scala 484:61:example.TestHarness.Sha3RocketConfig.fir@134977.32]
  assign _T_119 = {1'h1,_T_118}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134978.32]
  assign _buffer_pindex_15 = {{23'd0}, _T_119}; // @[ctrl.scala 484:30:example.TestHarness.Sha3RocketConfig.fir@134979.32 ctrl.scala 484:30:example.TestHarness.Sha3RocketConfig.fir@134979.32]
  assign _T_120 = byte_offset == 4'h6; // @[ctrl.scala 486:34:example.TestHarness.Sha3RocketConfig.fir@134982.32]
  assign _T_121 = _GEN_196[47:0]; // @[ctrl.scala 494:61:example.TestHarness.Sha3RocketConfig.fir@134984.34]
  assign _T_122 = {1'h1,_T_121}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134985.34]
  assign _buffer_pindex_17 = {{15'd0}, _T_122}; // @[ctrl.scala 494:30:example.TestHarness.Sha3RocketConfig.fir@134986.34 ctrl.scala 494:30:example.TestHarness.Sha3RocketConfig.fir@134986.34]
  assign _T_125 = {1'h1,_T_96}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@134992.36]
  assign _buffer_pindex_19 = {{7'd0}, _T_125}; // @[ctrl.scala 504:30:example.TestHarness.Sha3RocketConfig.fir@134993.36 ctrl.scala 504:30:example.TestHarness.Sha3RocketConfig.fir@134993.36]
  assign _T_126 = words_filled == 5'h0; // @[ctrl.scala 509:29:example.TestHarness.Sha3RocketConfig.fir@134997.22]
  assign _T_128 = _T_126 & _T_85; // @[ctrl.scala 509:41:example.TestHarness.Sha3RocketConfig.fir@134999.22]
  assign _GEN_656 = areg ? 5'h0 : buffer_count; // @[ctrl.scala 526:17:example.TestHarness.Sha3RocketConfig.fir@135009.14]
  assign _T_134 = pindex + 5'h1; // @[ctrl.scala 554:28:example.TestHarness.Sha3RocketConfig.fir@135031.16]
  assign _GEN_663 = next_buff_val ? _GEN_656 : buffer_count; // @[ctrl.scala 523:24:example.TestHarness.Sha3RocketConfig.fir@135007.12]
  assign _T_135 = 3'h4 == mem_s; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135038.12]
  assign _T_136 = aindex >= 5'h10; // @[ctrl.scala 563:17:example.TestHarness.Sha3RocketConfig.fir@135041.14]
  assign _GEN_668 = _T_135 | buffer_valid; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135039.12]
  assign _GEN_670 = _T_77 ? next_buff_val : _GEN_668; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134887.10]
  assign _GEN_689 = _T_77 ? _GEN_663 : buffer_count; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134887.10]
  assign _GEN_710 = _T_58 ? _GEN_124 : _GEN_689; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134829.8]
  assign _GEN_711 = _T_58 ? _GEN_125 : _GEN_670; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134829.8]
  assign _GEN_714 = _T_58 ? _GEN_128 : _GEN_28; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134829.8]
  assign _GEN_717 = _T_26 & _GEN_45; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_718 = _T_26 ? _GEN_46 : 64'h0; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_719 = _T_26 ? _GEN_47 : rindex; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_723 = _T_26 ? _GEN_71 : read; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_724 = _T_26 ? _GEN_67 : _GEN_711; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_727 = _T_26 ? _GEN_72 : _GEN_714; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_745 = _T_26 ? buffer_count : _GEN_710; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@134745.6]
  assign _GEN_747 = _T_15 ? _GEN_39 : _GEN_745; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _GEN_750 = _T_15 ? 1'h0 : _GEN_717; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _GEN_751 = _T_15 ? 64'h0 : _GEN_718; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _GEN_752 = _T_15 ? rindex : _GEN_719; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _GEN_755 = _T_15 ? read : _GEN_723; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _GEN_756 = _T_15 ? buffer_valid : _GEN_724; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _GEN_758 = _T_15 ? _GEN_28 : _GEN_727; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@134723.4]
  assign _T_137 = io_dmem_resp_tag[4:0]; // @[ctrl.scala 571:28:example.TestHarness.Sha3RocketConfig.fir@135047.6]
  assign _T_138 = _T_137 < 5'h11; // @[ctrl.scala 571:34:example.TestHarness.Sha3RocketConfig.fir@135048.6]
  assign _GEN_811 = _T_138 ? _T_62 : _GEN_747; // @[ctrl.scala 571:59:example.TestHarness.Sha3RocketConfig.fir@135049.6]
  assign _GEN_829 = io_dmem_resp_val ? _GEN_811 : _GEN_747; // @[ctrl.scala 570:28:example.TestHarness.Sha3RocketConfig.fir@135046.4]
  assign _T_143 = mindex >= 5'h11; // @[ctrl.scala 584:18:example.TestHarness.Sha3RocketConfig.fir@135058.4]
  assign _T_144 = _T_2 & _T_143; // @[ctrl.scala 583:35:example.TestHarness.Sha3RocketConfig.fir@135059.4]
  assign _T_145 = _GEN_1006 > msg_len; // @[ctrl.scala 586:17:example.TestHarness.Sha3RocketConfig.fir@135061.6]
  assign _GEN_830 = _T_145 ? _GEN_756 : 1'h1; // @[ctrl.scala 586:27:example.TestHarness.Sha3RocketConfig.fir@135062.6]
  assign _GEN_831 = _T_144 ? _GEN_830 : _GEN_756; // @[ctrl.scala 584:45:example.TestHarness.Sha3RocketConfig.fir@135060.4]
  assign _T_146 = 3'h0 == state; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135068.4]
  assign _T_147 = rindex_reg >= 5'h18; // @[ctrl.scala 599:39:example.TestHarness.Sha3RocketConfig.fir@135070.6]
  assign _T_148 = _T_147 & buffer_valid; // @[ctrl.scala 599:55:example.TestHarness.Sha3RocketConfig.fir@135071.6]
  assign _GEN_1017 = {{32'd0}, hashed}; // @[ctrl.scala 599:81:example.TestHarness.Sha3RocketConfig.fir@135072.6]
  assign _T_149 = _GEN_1017 <= msg_len; // @[ctrl.scala 599:81:example.TestHarness.Sha3RocketConfig.fir@135072.6]
  assign _T_150 = _T_148 & _T_149; // @[ctrl.scala 599:71:example.TestHarness.Sha3RocketConfig.fir@135073.6]
  assign _T_151 = busy & _T_150; // @[ctrl.scala 599:24:example.TestHarness.Sha3RocketConfig.fir@135074.6]
  assign _GEN_832 = _T_151 | _GEN_31; // @[ctrl.scala 600:20:example.TestHarness.Sha3RocketConfig.fir@135075.6]
  assign _T_152 = 3'h1 == state; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135084.6]
  assign _T_153 = areg == 1'h0; // @[ctrl.scala 608:17:example.TestHarness.Sha3RocketConfig.fir@135086.8]
  assign _T_155 = aindex + 5'h1; // @[ctrl.scala 610:22:example.TestHarness.Sha3RocketConfig.fir@135090.8]
  assign _T_156 = io_aindex >= 5'h10; // @[ctrl.scala 611:20:example.TestHarness.Sha3RocketConfig.fir@135092.8]
  assign _T_158 = hashed + 32'h88; // @[ctrl.scala 619:24:example.TestHarness.Sha3RocketConfig.fir@135100.10]
  assign _T_159 = 3'h2 == state; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135109.8]
  assign _T_160 = 3'h3 == state; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135115.10]
  assign _T_161 = rindex < 5'h18; // @[ctrl.scala 631:17:example.TestHarness.Sha3RocketConfig.fir@135117.12]
  assign _T_166 = rindex + 5'h1; // @[ctrl.scala 640:24:example.TestHarness.Sha3RocketConfig.fir@135132.16]
  assign _T_167 = _GEN_1017 > msg_len; // @[ctrl.scala 647:19:example.TestHarness.Sha3RocketConfig.fir@135141.14]
  assign _T_168 = _GEN_1017 == msg_len; // @[ctrl.scala 647:40:example.TestHarness.Sha3RocketConfig.fir@135142.14]
  assign _T_169 = rindex == 5'h18; // @[ctrl.scala 647:62:example.TestHarness.Sha3RocketConfig.fir@135143.14]
  assign _T_170 = _T_168 & _T_169; // @[ctrl.scala 647:52:example.TestHarness.Sha3RocketConfig.fir@135144.14]
  assign _T_171 = _T_167 | _T_170; // @[ctrl.scala 647:29:example.TestHarness.Sha3RocketConfig.fir@135145.14]
  assign _GEN_852 = _T_161 ? 1'h0 : 1'h1; // @[ctrl.scala 631:32:example.TestHarness.Sha3RocketConfig.fir@135118.12]
  assign _T_172 = 3'h4 == state; // @[Conditional.scala 37:30:example.TestHarness.Sha3RocketConfig.fir@135156.12]
  assign _T_173 = windex < 3'h4; // @[ctrl.scala 658:31:example.TestHarness.Sha3RocketConfig.fir@135158.14]
  assign _T_174 = {windex, 3'h0}; // @[ctrl.scala 659:45:example.TestHarness.Sha3RocketConfig.fir@135160.14]
  assign _GEN_1020 = {{58'd0}, _T_174}; // @[ctrl.scala 659:35:example.TestHarness.Sha3RocketConfig.fir@135161.14]
  assign _T_176 = hash_addr + _GEN_1020; // @[ctrl.scala 659:35:example.TestHarness.Sha3RocketConfig.fir@135162.14]
  assign _GEN_1021 = {{2'd0}, windex}; // @[ctrl.scala 660:47:example.TestHarness.Sha3RocketConfig.fir@135164.14]
  assign _T_178 = 5'h11 + _GEN_1021; // @[ctrl.scala 660:47:example.TestHarness.Sha3RocketConfig.fir@135165.14]
  assign _T_180 = windex + 3'h1; // @[ctrl.scala 664:24:example.TestHarness.Sha3RocketConfig.fir@135170.16]
  assign _T_181 = dmem_resp_tag_reg[4:0]; // @[ctrl.scala 670:29:example.TestHarness.Sha3RocketConfig.fir@135174.16]
  assign _T_182 = _T_181 >= 5'h11; // @[ctrl.scala 670:35:example.TestHarness.Sha3RocketConfig.fir@135175.16]
  assign _T_185 = _T_181 - 5'h11; // @[ctrl.scala 672:43:example.TestHarness.Sha3RocketConfig.fir@135179.18]
  assign _T_186 = _T_185[1:0]; // @[:example.TestHarness.Sha3RocketConfig.fir@135180.18]
  assign _GEN_1022 = 2'h0 == _T_186; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_857 = _GEN_1022 | writes_done_0; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_1023 = 2'h1 == _T_186; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_858 = _GEN_1023 | writes_done_1; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_1024 = 2'h2 == _T_186; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_859 = _GEN_1024 | writes_done_2; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_1025 = 2'h3 == _T_186; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _GEN_860 = _GEN_1025 | writes_done_3; // @[ctrl.scala 672:68:example.TestHarness.Sha3RocketConfig.fir@135181.18]
  assign _T_187 = writes_done_0 & writes_done_1; // @[ctrl.scala 675:30:example.TestHarness.Sha3RocketConfig.fir@135184.14]
  assign _T_188 = _T_187 & writes_done_2; // @[ctrl.scala 675:30:example.TestHarness.Sha3RocketConfig.fir@135185.14]
  assign _T_189 = _T_188 & writes_done_3; // @[ctrl.scala 675:30:example.TestHarness.Sha3RocketConfig.fir@135186.14]
  assign _GEN_885 = _T_172 ? _T_173 : _GEN_750; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  assign _GEN_886 = _T_172 ? _T_176 : _GEN_751; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  assign _GEN_887 = _T_172 ? _T_178 : _GEN_752; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  assign _GEN_903 = _T_172 & _T_189; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135157.12]
  assign _GEN_908 = _T_160 ? _GEN_852 : 1'h1; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  assign _GEN_912 = _T_160 ? _GEN_750 : _GEN_885; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  assign _GEN_913 = _T_160 ? _GEN_751 : _GEN_886; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  assign _GEN_914 = _T_160 ? _GEN_752 : _GEN_887; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  assign _GEN_915 = _T_160 ? 1'h0 : _T_172; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  assign _GEN_928 = _T_160 ? 1'h0 : _GEN_903; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135116.10]
  assign _GEN_934 = _T_159 | _GEN_908; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  assign _GEN_937 = _T_159 ? _GEN_750 : _GEN_912; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  assign _GEN_938 = _T_159 ? _GEN_751 : _GEN_913; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  assign _GEN_939 = _T_159 ? _GEN_752 : _GEN_914; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  assign _GEN_940 = _T_159 ? 1'h0 : _GEN_915; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  assign _GEN_953 = _T_159 ? 1'h0 : _GEN_928; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135110.8]
  assign _GEN_954 = _T_152 ? _T_153 : _GEN_934; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  assign _GEN_966 = _T_152 ? _GEN_750 : _GEN_937; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  assign _GEN_967 = _T_152 ? _GEN_751 : _GEN_938; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  assign _GEN_968 = _T_152 ? _GEN_752 : _GEN_939; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  assign _GEN_969 = _T_152 ? 1'h0 : _GEN_940; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  assign _GEN_979 = _T_152 ? 1'h0 : _GEN_953; // @[Conditional.scala 39:67:example.TestHarness.Sha3RocketConfig.fir@135085.6]
  assign _GEN_994 = _T_146 ? _GEN_751 : _GEN_967; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135069.4]
  assign _GEN_995 = _T_146 ? _GEN_752 : _GEN_968; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135069.4]
  assign _GEN_996 = _T_146 ? 1'h0 : _GEN_969; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135069.4]
  assign io_rocc_req_rdy = _T_12 ? _GEN_21 : _T_10; // @[ctrl.scala 173:19:example.TestHarness.Sha3RocketConfig.fir@134685.4 ctrl.scala 190:21:example.TestHarness.Sha3RocketConfig.fir@134701.6 ctrl.scala 193:25:example.TestHarness.Sha3RocketConfig.fir@134707.10 ctrl.scala 200:25:example.TestHarness.Sha3RocketConfig.fir@134716.12]
  assign io_busy = _T_12 ? _GEN_24 : busy; // @[ctrl.scala 175:11:example.TestHarness.Sha3RocketConfig.fir@134687.4 ctrl.scala 197:17:example.TestHarness.Sha3RocketConfig.fir@134710.10 ctrl.scala 201:17:example.TestHarness.Sha3RocketConfig.fir@134717.12]
  assign io_dmem_req_val = _T_146 ? _GEN_750 : _GEN_966; // @[ctrl.scala 179:18:example.TestHarness.Sha3RocketConfig.fir@134691.4 ctrl.scala 233:23:example.TestHarness.Sha3RocketConfig.fir@134751.10 ctrl.scala 658:21:example.TestHarness.Sha3RocketConfig.fir@135159.14]
  assign io_dmem_req_tag = {{2'd0}, _GEN_995}; // @[ctrl.scala 180:18:example.TestHarness.Sha3RocketConfig.fir@134692.4 ctrl.scala 235:23:example.TestHarness.Sha3RocketConfig.fir@134756.10 ctrl.scala 660:21:example.TestHarness.Sha3RocketConfig.fir@135166.14]
  assign io_dmem_req_addr = _GEN_994[31:0]; // @[ctrl.scala 181:19:example.TestHarness.Sha3RocketConfig.fir@134693.4 ctrl.scala 234:24:example.TestHarness.Sha3RocketConfig.fir@134755.10 ctrl.scala 659:22:example.TestHarness.Sha3RocketConfig.fir@135163.14]
  assign io_dmem_req_cmd = {{4'd0}, _GEN_996}; // @[ctrl.scala 182:18:example.TestHarness.Sha3RocketConfig.fir@134694.4 ctrl.scala 236:23:example.TestHarness.Sha3RocketConfig.fir@134757.10 ctrl.scala 661:21:example.TestHarness.Sha3RocketConfig.fir@135167.14]
  assign io_round = rindex; // @[ctrl.scala 176:18:example.TestHarness.Sha3RocketConfig.fir@134688.4 ctrl.scala 634:18:example.TestHarness.Sha3RocketConfig.fir@135124.16 ctrl.scala 641:16:example.TestHarness.Sha3RocketConfig.fir@135134.16]
  assign io_absorb = areg; // @[ctrl.scala 130:17:example.TestHarness.Sha3RocketConfig.fir@134672.4]
  assign io_aindex = _T_5; // @[ctrl.scala 129:17:example.TestHarness.Sha3RocketConfig.fir@134671.4]
  assign io_init = _T_146 ? 1'h0 : _GEN_979; // @[ctrl.scala 174:11:example.TestHarness.Sha3RocketConfig.fir@134686.4 ctrl.scala 690:15:example.TestHarness.Sha3RocketConfig.fir@135205.16]
  assign io_write = _T_146 | _GEN_954; // @[ctrl.scala 178:18:example.TestHarness.Sha3RocketConfig.fir@134690.4 ctrl.scala 608:14:example.TestHarness.Sha3RocketConfig.fir@135087.8 ctrl.scala 636:16:example.TestHarness.Sha3RocketConfig.fir@135126.16 ctrl.scala 642:16:example.TestHarness.Sha3RocketConfig.fir@135135.16 ctrl.scala 646:16:example.TestHarness.Sha3RocketConfig.fir@135140.14]
  assign io_windex = windex; // @[ctrl.scala 145:17:example.TestHarness.Sha3RocketConfig.fir@134675.4]
  assign io_buffer_out = 5'h10 == io_aindex ? buffer_16 : _GEN_15; // @[ctrl.scala 143:19:example.TestHarness.Sha3RocketConfig.fir@134674.4]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {2{`RANDOM}};
  msg_addr = _RAND_0[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {2{`RANDOM}};
  hash_addr = _RAND_1[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {2{`RANDOM}};
  msg_len = _RAND_2[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_3 = {1{`RANDOM}};
  busy = _RAND_3[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_4 = {1{`RANDOM}};
  dmem_resp_tag_reg = _RAND_4[6:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_5 = {1{`RANDOM}};
  mem_s = _RAND_5[2:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_6 = {2{`RANDOM}};
  buffer_0 = _RAND_6[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_7 = {2{`RANDOM}};
  buffer_1 = _RAND_7[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_8 = {2{`RANDOM}};
  buffer_2 = _RAND_8[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_9 = {2{`RANDOM}};
  buffer_3 = _RAND_9[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_10 = {2{`RANDOM}};
  buffer_4 = _RAND_10[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_11 = {2{`RANDOM}};
  buffer_5 = _RAND_11[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_12 = {2{`RANDOM}};
  buffer_6 = _RAND_12[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_13 = {2{`RANDOM}};
  buffer_7 = _RAND_13[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_14 = {2{`RANDOM}};
  buffer_8 = _RAND_14[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_15 = {2{`RANDOM}};
  buffer_9 = _RAND_15[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_16 = {2{`RANDOM}};
  buffer_10 = _RAND_16[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_17 = {2{`RANDOM}};
  buffer_11 = _RAND_17[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_18 = {2{`RANDOM}};
  buffer_12 = _RAND_18[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_19 = {2{`RANDOM}};
  buffer_13 = _RAND_19[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_20 = {2{`RANDOM}};
  buffer_14 = _RAND_20[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_21 = {2{`RANDOM}};
  buffer_15 = _RAND_21[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_22 = {2{`RANDOM}};
  buffer_16 = _RAND_22[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_23 = {1{`RANDOM}};
  buffer_valid = _RAND_23[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_24 = {1{`RANDOM}};
  buffer_count = _RAND_24[4:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_25 = {1{`RANDOM}};
  read = _RAND_25[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_26 = {1{`RANDOM}};
  hashed = _RAND_26[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_27 = {1{`RANDOM}};
  areg = _RAND_27[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_28 = {1{`RANDOM}};
  mindex = _RAND_28[4:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_29 = {1{`RANDOM}};
  windex = _RAND_29[2:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_30 = {1{`RANDOM}};
  aindex = _RAND_30[4:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_31 = {1{`RANDOM}};
  pindex = _RAND_31[4:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_32 = {1{`RANDOM}};
  writes_done_0 = _RAND_32[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_33 = {1{`RANDOM}};
  writes_done_1 = _RAND_33[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_34 = {1{`RANDOM}};
  writes_done_2 = _RAND_34[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_35 = {1{`RANDOM}};
  writes_done_3 = _RAND_35[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_36 = {1{`RANDOM}};
  next_buff_val = _RAND_36[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_37 = {1{`RANDOM}};
  _T_5 = _RAND_37[4:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_38 = {1{`RANDOM}};
  state = _RAND_38[2:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_39 = {1{`RANDOM}};
  rindex = _RAND_39[4:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_40 = {1{`RANDOM}};
  rindex_reg = _RAND_40[4:0];
  `endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end
  always @(posedge clock) begin
    if (reset) begin
      msg_addr <= 64'h0;
    end else begin
      if (_T_146) begin
        if (_T_15) begin
          if (_T_12) begin
            if (_T_13) begin
              msg_addr <= io_rocc_rs1;
            end
          end
        end else begin
          if (_T_26) begin
            if (_T_40) begin
              if (_T_12) begin
                if (_T_13) begin
                  msg_addr <= io_rocc_rs1;
                end
              end
            end else begin
              if (_T_48) begin
                if (_T_12) begin
                  if (_T_13) begin
                    msg_addr <= io_rocc_rs1;
                  end
                end
              end else begin
                msg_addr <= _T_54;
              end
            end
          end else begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (_T_40) begin
                  if (_T_12) begin
                    if (_T_13) begin
                      msg_addr <= io_rocc_rs1;
                    end
                  end
                end else begin
                  if (_T_48) begin
                    msg_addr <= _GEN_28;
                  end else begin
                    msg_addr <= _T_54;
                  end
                end
              end else begin
                msg_addr <= _GEN_28;
              end
            end else begin
              msg_addr <= _GEN_28;
            end
          end
        end
      end else begin
        if (_T_152) begin
          if (_T_15) begin
            msg_addr <= _GEN_28;
          end else begin
            if (_T_26) begin
              if (_T_40) begin
                msg_addr <= _GEN_28;
              end else begin
                if (_T_48) begin
                  msg_addr <= _GEN_28;
                end else begin
                  msg_addr <= _T_54;
                end
              end
            end else begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (_T_40) begin
                    msg_addr <= _GEN_28;
                  end else begin
                    if (_T_48) begin
                      msg_addr <= _GEN_28;
                    end else begin
                      msg_addr <= _T_54;
                    end
                  end
                end else begin
                  msg_addr <= _GEN_28;
                end
              end else begin
                msg_addr <= _GEN_28;
              end
            end
          end
        end else begin
          if (_T_159) begin
            if (_T_15) begin
              msg_addr <= _GEN_28;
            end else begin
              if (_T_26) begin
                msg_addr <= _GEN_72;
              end else begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    msg_addr <= _GEN_72;
                  end else begin
                    msg_addr <= _GEN_28;
                  end
                end else begin
                  msg_addr <= _GEN_28;
                end
              end
            end
          end else begin
            if (_T_160) begin
              if (_T_15) begin
                msg_addr <= _GEN_28;
              end else begin
                if (_T_26) begin
                  msg_addr <= _GEN_72;
                end else begin
                  if (_T_58) begin
                    if (io_dmem_resp_val) begin
                      msg_addr <= _GEN_72;
                    end else begin
                      msg_addr <= _GEN_28;
                    end
                  end else begin
                    msg_addr <= _GEN_28;
                  end
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  msg_addr <= 64'h0;
                end else begin
                  msg_addr <= _GEN_758;
                end
              end else begin
                msg_addr <= _GEN_758;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      hash_addr <= 64'h0;
    end else begin
      if (_T_146) begin
        if (_T_12) begin
          if (_T_13) begin
            hash_addr <= io_rocc_rs2;
          end
        end
      end else begin
        if (_T_152) begin
          if (_T_12) begin
            if (_T_13) begin
              hash_addr <= io_rocc_rs2;
            end
          end
        end else begin
          if (_T_159) begin
            if (_T_12) begin
              if (_T_13) begin
                hash_addr <= io_rocc_rs2;
              end
            end
          end else begin
            if (_T_160) begin
              if (_T_12) begin
                if (_T_13) begin
                  hash_addr <= io_rocc_rs2;
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  hash_addr <= 64'h0;
                end else begin
                  hash_addr <= _GEN_29;
                end
              end else begin
                hash_addr <= _GEN_29;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      msg_len <= 64'h0;
    end else begin
      if (_T_146) begin
        if (_T_12) begin
          if (!(_T_13)) begin
            if (_T_14) begin
              msg_len <= io_rocc_rs1;
            end
          end
        end
      end else begin
        if (_T_152) begin
          if (_T_12) begin
            if (!(_T_13)) begin
              if (_T_14) begin
                msg_len <= io_rocc_rs1;
              end
            end
          end
        end else begin
          if (_T_159) begin
            if (_T_12) begin
              if (!(_T_13)) begin
                if (_T_14) begin
                  msg_len <= io_rocc_rs1;
                end
              end
            end
          end else begin
            if (_T_160) begin
              if (_T_12) begin
                if (!(_T_13)) begin
                  if (_T_14) begin
                    msg_len <= io_rocc_rs1;
                  end
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  msg_len <= 64'h0;
                end else begin
                  msg_len <= _GEN_32;
                end
              end else begin
                msg_len <= _GEN_32;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      busy <= 1'h0;
    end else begin
      if (_T_146) begin
        busy <= _GEN_832;
      end else begin
        if (_T_152) begin
          if (_T_12) begin
            if (!(_T_13)) begin
              busy <= _GEN_17;
            end
          end
        end else begin
          if (_T_159) begin
            if (_T_12) begin
              if (!(_T_13)) begin
                busy <= _GEN_17;
              end
            end
          end else begin
            if (_T_160) begin
              if (_T_12) begin
                if (!(_T_13)) begin
                  busy <= _GEN_17;
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  busy <= 1'h0;
                end else begin
                  if (_T_12) begin
                    if (!(_T_13)) begin
                      busy <= _GEN_17;
                    end
                  end
                end
              end else begin
                busy <= _GEN_31;
              end
            end
          end
        end
      end
    end
    dmem_resp_tag_reg <= io_dmem_resp_tag;
    if (reset) begin
      mem_s <= 3'h0;
    end else begin
      if (_T_15) begin
        if (_T_25) begin
          mem_s <= 3'h1;
        end else begin
          mem_s <= 3'h0;
        end
      end else begin
        if (_T_26) begin
          if (_T_40) begin
            if (_T_41) begin
              mem_s <= 3'h1;
            end else begin
              mem_s <= 3'h3;
            end
          end else begin
            if (_T_48) begin
              mem_s <= 3'h1;
            end else begin
              if (_T_57) begin
                mem_s <= 3'h3;
              end else begin
                mem_s <= 3'h0;
              end
            end
          end
        end else begin
          if (_T_58) begin
            if (io_dmem_resp_val) begin
              if (_T_40) begin
                if (_T_41) begin
                  mem_s <= 3'h1;
                end else begin
                  mem_s <= 3'h3;
                end
              end else begin
                if (_T_48) begin
                  mem_s <= 3'h1;
                end else begin
                  if (_T_57) begin
                    mem_s <= 3'h3;
                  end else begin
                    mem_s <= 3'h0;
                  end
                end
              end
            end
          end else begin
            if (_T_77) begin
              if (next_buff_val) begin
                if (areg) begin
                  mem_s <= 3'h0;
                end else begin
                  mem_s <= 3'h4;
                end
              end else begin
                mem_s <= 3'h3;
              end
            end else begin
              if (_T_135) begin
                if (_T_136) begin
                  mem_s <= 3'h0;
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_0 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h0 == _T_137) begin
            buffer_0 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h0 == _T_8) begin
                      buffer_0 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h0 == pindex) begin
                            buffer_0 <= 64'h1;
                          end
                        end else begin
                          if (5'h0 == pindex) begin
                            buffer_0 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h0 == pindex) begin
                                buffer_0 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h0 == pindex) begin
                                buffer_0 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h0 == pindex) begin
                                buffer_0 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h0 == pindex) begin
                                  buffer_0 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h0 == pindex) begin
                                    buffer_0 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h0 == pindex) begin
                                      buffer_0 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h0 == pindex) begin
                                        buffer_0 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h0 == pindex) begin
                                          buffer_0 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h0 == pindex) begin
                                            buffer_0 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h0 == pindex) begin
                                              buffer_0 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h0 == pindex) begin
                                  buffer_0 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h0 == _T_8) begin
                    buffer_0 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h0 == pindex) begin
                          buffer_0 <= 64'h1;
                        end
                      end else begin
                        if (5'h0 == pindex) begin
                          buffer_0 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h0 == pindex) begin
                              buffer_0 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h0 == pindex) begin
                              buffer_0 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h0 == pindex) begin
                              buffer_0 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h0 == pindex) begin
                                buffer_0 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h0 == pindex) begin
                                  buffer_0 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h0 == pindex) begin
                                    buffer_0 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h0 == pindex) begin
                                      buffer_0 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h0 == pindex) begin
                                        buffer_0 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h0 == pindex) begin
                                          buffer_0 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h0 == pindex) begin
                                            buffer_0 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h0 == pindex) begin
                                buffer_0 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h0 == _T_8) begin
                  buffer_0 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_0 <= _GEN_129;
                    end else begin
                      if (5'h0 == pindex) begin
                        buffer_0 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h0 == pindex) begin
                            buffer_0 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h0 == pindex) begin
                            buffer_0 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h0 == pindex) begin
                            buffer_0 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h0 == pindex) begin
                              buffer_0 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h0 == pindex) begin
                                buffer_0 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h0 == pindex) begin
                                  buffer_0 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h0 == pindex) begin
                                    buffer_0 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h0 == pindex) begin
                                      buffer_0 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h0 == pindex) begin
                                        buffer_0 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h0 == pindex) begin
                                          buffer_0 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_0 <= _GEN_129;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_1 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h1 == _T_137) begin
            buffer_1 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h1 == _T_8) begin
                      buffer_1 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h1 == pindex) begin
                            buffer_1 <= 64'h1;
                          end
                        end else begin
                          if (5'h1 == pindex) begin
                            buffer_1 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h1 == pindex) begin
                                buffer_1 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h1 == pindex) begin
                                buffer_1 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h1 == pindex) begin
                                buffer_1 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h1 == pindex) begin
                                  buffer_1 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h1 == pindex) begin
                                    buffer_1 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h1 == pindex) begin
                                      buffer_1 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h1 == pindex) begin
                                        buffer_1 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h1 == pindex) begin
                                          buffer_1 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h1 == pindex) begin
                                            buffer_1 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h1 == pindex) begin
                                              buffer_1 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h1 == pindex) begin
                                  buffer_1 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h1 == _T_8) begin
                    buffer_1 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h1 == pindex) begin
                          buffer_1 <= 64'h1;
                        end
                      end else begin
                        if (5'h1 == pindex) begin
                          buffer_1 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h1 == pindex) begin
                              buffer_1 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h1 == pindex) begin
                              buffer_1 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h1 == pindex) begin
                              buffer_1 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h1 == pindex) begin
                                buffer_1 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h1 == pindex) begin
                                  buffer_1 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h1 == pindex) begin
                                    buffer_1 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h1 == pindex) begin
                                      buffer_1 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h1 == pindex) begin
                                        buffer_1 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h1 == pindex) begin
                                          buffer_1 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h1 == pindex) begin
                                            buffer_1 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h1 == pindex) begin
                                buffer_1 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h1 == _T_8) begin
                  buffer_1 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_1 <= _GEN_130;
                    end else begin
                      if (5'h1 == pindex) begin
                        buffer_1 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h1 == pindex) begin
                            buffer_1 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h1 == pindex) begin
                            buffer_1 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h1 == pindex) begin
                            buffer_1 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h1 == pindex) begin
                              buffer_1 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h1 == pindex) begin
                                buffer_1 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h1 == pindex) begin
                                  buffer_1 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h1 == pindex) begin
                                    buffer_1 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h1 == pindex) begin
                                      buffer_1 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h1 == pindex) begin
                                        buffer_1 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h1 == pindex) begin
                                          buffer_1 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_1 <= _GEN_130;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_2 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h2 == _T_137) begin
            buffer_2 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h2 == _T_8) begin
                      buffer_2 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h2 == pindex) begin
                            buffer_2 <= 64'h1;
                          end
                        end else begin
                          if (5'h2 == pindex) begin
                            buffer_2 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h2 == pindex) begin
                                buffer_2 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h2 == pindex) begin
                                buffer_2 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h2 == pindex) begin
                                buffer_2 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h2 == pindex) begin
                                  buffer_2 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h2 == pindex) begin
                                    buffer_2 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h2 == pindex) begin
                                      buffer_2 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h2 == pindex) begin
                                        buffer_2 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h2 == pindex) begin
                                          buffer_2 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h2 == pindex) begin
                                            buffer_2 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h2 == pindex) begin
                                              buffer_2 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h2 == pindex) begin
                                  buffer_2 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h2 == _T_8) begin
                    buffer_2 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h2 == pindex) begin
                          buffer_2 <= 64'h1;
                        end
                      end else begin
                        if (5'h2 == pindex) begin
                          buffer_2 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h2 == pindex) begin
                              buffer_2 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h2 == pindex) begin
                              buffer_2 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h2 == pindex) begin
                              buffer_2 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h2 == pindex) begin
                                buffer_2 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h2 == pindex) begin
                                  buffer_2 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h2 == pindex) begin
                                    buffer_2 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h2 == pindex) begin
                                      buffer_2 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h2 == pindex) begin
                                        buffer_2 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h2 == pindex) begin
                                          buffer_2 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h2 == pindex) begin
                                            buffer_2 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h2 == pindex) begin
                                buffer_2 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h2 == _T_8) begin
                  buffer_2 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_2 <= _GEN_131;
                    end else begin
                      if (5'h2 == pindex) begin
                        buffer_2 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h2 == pindex) begin
                            buffer_2 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h2 == pindex) begin
                            buffer_2 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h2 == pindex) begin
                            buffer_2 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h2 == pindex) begin
                              buffer_2 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h2 == pindex) begin
                                buffer_2 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h2 == pindex) begin
                                  buffer_2 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h2 == pindex) begin
                                    buffer_2 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h2 == pindex) begin
                                      buffer_2 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h2 == pindex) begin
                                        buffer_2 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h2 == pindex) begin
                                          buffer_2 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_2 <= _GEN_131;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_3 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h3 == _T_137) begin
            buffer_3 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h3 == _T_8) begin
                      buffer_3 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h3 == pindex) begin
                            buffer_3 <= 64'h1;
                          end
                        end else begin
                          if (5'h3 == pindex) begin
                            buffer_3 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h3 == pindex) begin
                                buffer_3 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h3 == pindex) begin
                                buffer_3 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h3 == pindex) begin
                                buffer_3 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h3 == pindex) begin
                                  buffer_3 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h3 == pindex) begin
                                    buffer_3 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h3 == pindex) begin
                                      buffer_3 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h3 == pindex) begin
                                        buffer_3 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h3 == pindex) begin
                                          buffer_3 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h3 == pindex) begin
                                            buffer_3 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h3 == pindex) begin
                                              buffer_3 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h3 == pindex) begin
                                  buffer_3 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h3 == _T_8) begin
                    buffer_3 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h3 == pindex) begin
                          buffer_3 <= 64'h1;
                        end
                      end else begin
                        if (5'h3 == pindex) begin
                          buffer_3 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h3 == pindex) begin
                              buffer_3 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h3 == pindex) begin
                              buffer_3 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h3 == pindex) begin
                              buffer_3 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h3 == pindex) begin
                                buffer_3 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h3 == pindex) begin
                                  buffer_3 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h3 == pindex) begin
                                    buffer_3 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h3 == pindex) begin
                                      buffer_3 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h3 == pindex) begin
                                        buffer_3 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h3 == pindex) begin
                                          buffer_3 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h3 == pindex) begin
                                            buffer_3 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h3 == pindex) begin
                                buffer_3 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h3 == _T_8) begin
                  buffer_3 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_3 <= _GEN_132;
                    end else begin
                      if (5'h3 == pindex) begin
                        buffer_3 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h3 == pindex) begin
                            buffer_3 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h3 == pindex) begin
                            buffer_3 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h3 == pindex) begin
                            buffer_3 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h3 == pindex) begin
                              buffer_3 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h3 == pindex) begin
                                buffer_3 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h3 == pindex) begin
                                  buffer_3 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h3 == pindex) begin
                                    buffer_3 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h3 == pindex) begin
                                      buffer_3 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h3 == pindex) begin
                                        buffer_3 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h3 == pindex) begin
                                          buffer_3 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_3 <= _GEN_132;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_4 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h4 == _T_137) begin
            buffer_4 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h4 == _T_8) begin
                      buffer_4 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h4 == pindex) begin
                            buffer_4 <= 64'h1;
                          end
                        end else begin
                          if (5'h4 == pindex) begin
                            buffer_4 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h4 == pindex) begin
                                buffer_4 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h4 == pindex) begin
                                buffer_4 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h4 == pindex) begin
                                buffer_4 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h4 == pindex) begin
                                  buffer_4 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h4 == pindex) begin
                                    buffer_4 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h4 == pindex) begin
                                      buffer_4 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h4 == pindex) begin
                                        buffer_4 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h4 == pindex) begin
                                          buffer_4 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h4 == pindex) begin
                                            buffer_4 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h4 == pindex) begin
                                              buffer_4 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h4 == pindex) begin
                                  buffer_4 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h4 == _T_8) begin
                    buffer_4 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h4 == pindex) begin
                          buffer_4 <= 64'h1;
                        end
                      end else begin
                        if (5'h4 == pindex) begin
                          buffer_4 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h4 == pindex) begin
                              buffer_4 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h4 == pindex) begin
                              buffer_4 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h4 == pindex) begin
                              buffer_4 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h4 == pindex) begin
                                buffer_4 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h4 == pindex) begin
                                  buffer_4 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h4 == pindex) begin
                                    buffer_4 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h4 == pindex) begin
                                      buffer_4 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h4 == pindex) begin
                                        buffer_4 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h4 == pindex) begin
                                          buffer_4 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h4 == pindex) begin
                                            buffer_4 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h4 == pindex) begin
                                buffer_4 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h4 == _T_8) begin
                  buffer_4 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_4 <= _GEN_133;
                    end else begin
                      if (5'h4 == pindex) begin
                        buffer_4 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h4 == pindex) begin
                            buffer_4 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h4 == pindex) begin
                            buffer_4 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h4 == pindex) begin
                            buffer_4 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h4 == pindex) begin
                              buffer_4 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h4 == pindex) begin
                                buffer_4 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h4 == pindex) begin
                                  buffer_4 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h4 == pindex) begin
                                    buffer_4 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h4 == pindex) begin
                                      buffer_4 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h4 == pindex) begin
                                        buffer_4 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h4 == pindex) begin
                                          buffer_4 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_4 <= _GEN_133;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_5 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h5 == _T_137) begin
            buffer_5 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h5 == _T_8) begin
                      buffer_5 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h5 == pindex) begin
                            buffer_5 <= 64'h1;
                          end
                        end else begin
                          if (5'h5 == pindex) begin
                            buffer_5 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h5 == pindex) begin
                                buffer_5 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h5 == pindex) begin
                                buffer_5 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h5 == pindex) begin
                                buffer_5 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h5 == pindex) begin
                                  buffer_5 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h5 == pindex) begin
                                    buffer_5 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h5 == pindex) begin
                                      buffer_5 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h5 == pindex) begin
                                        buffer_5 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h5 == pindex) begin
                                          buffer_5 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h5 == pindex) begin
                                            buffer_5 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h5 == pindex) begin
                                              buffer_5 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h5 == pindex) begin
                                  buffer_5 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h5 == _T_8) begin
                    buffer_5 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h5 == pindex) begin
                          buffer_5 <= 64'h1;
                        end
                      end else begin
                        if (5'h5 == pindex) begin
                          buffer_5 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h5 == pindex) begin
                              buffer_5 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h5 == pindex) begin
                              buffer_5 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h5 == pindex) begin
                              buffer_5 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h5 == pindex) begin
                                buffer_5 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h5 == pindex) begin
                                  buffer_5 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h5 == pindex) begin
                                    buffer_5 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h5 == pindex) begin
                                      buffer_5 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h5 == pindex) begin
                                        buffer_5 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h5 == pindex) begin
                                          buffer_5 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h5 == pindex) begin
                                            buffer_5 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h5 == pindex) begin
                                buffer_5 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h5 == _T_8) begin
                  buffer_5 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_5 <= _GEN_134;
                    end else begin
                      if (5'h5 == pindex) begin
                        buffer_5 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h5 == pindex) begin
                            buffer_5 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h5 == pindex) begin
                            buffer_5 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h5 == pindex) begin
                            buffer_5 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h5 == pindex) begin
                              buffer_5 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h5 == pindex) begin
                                buffer_5 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h5 == pindex) begin
                                  buffer_5 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h5 == pindex) begin
                                    buffer_5 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h5 == pindex) begin
                                      buffer_5 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h5 == pindex) begin
                                        buffer_5 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h5 == pindex) begin
                                          buffer_5 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_5 <= _GEN_134;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_6 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h6 == _T_137) begin
            buffer_6 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h6 == _T_8) begin
                      buffer_6 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h6 == pindex) begin
                            buffer_6 <= 64'h1;
                          end
                        end else begin
                          if (5'h6 == pindex) begin
                            buffer_6 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h6 == pindex) begin
                                buffer_6 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h6 == pindex) begin
                                buffer_6 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h6 == pindex) begin
                                buffer_6 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h6 == pindex) begin
                                  buffer_6 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h6 == pindex) begin
                                    buffer_6 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h6 == pindex) begin
                                      buffer_6 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h6 == pindex) begin
                                        buffer_6 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h6 == pindex) begin
                                          buffer_6 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h6 == pindex) begin
                                            buffer_6 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h6 == pindex) begin
                                              buffer_6 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h6 == pindex) begin
                                  buffer_6 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h6 == _T_8) begin
                    buffer_6 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h6 == pindex) begin
                          buffer_6 <= 64'h1;
                        end
                      end else begin
                        if (5'h6 == pindex) begin
                          buffer_6 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h6 == pindex) begin
                              buffer_6 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h6 == pindex) begin
                              buffer_6 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h6 == pindex) begin
                              buffer_6 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h6 == pindex) begin
                                buffer_6 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h6 == pindex) begin
                                  buffer_6 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h6 == pindex) begin
                                    buffer_6 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h6 == pindex) begin
                                      buffer_6 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h6 == pindex) begin
                                        buffer_6 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h6 == pindex) begin
                                          buffer_6 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h6 == pindex) begin
                                            buffer_6 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h6 == pindex) begin
                                buffer_6 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h6 == _T_8) begin
                  buffer_6 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_6 <= _GEN_135;
                    end else begin
                      if (5'h6 == pindex) begin
                        buffer_6 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h6 == pindex) begin
                            buffer_6 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h6 == pindex) begin
                            buffer_6 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h6 == pindex) begin
                            buffer_6 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h6 == pindex) begin
                              buffer_6 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h6 == pindex) begin
                                buffer_6 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h6 == pindex) begin
                                  buffer_6 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h6 == pindex) begin
                                    buffer_6 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h6 == pindex) begin
                                      buffer_6 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h6 == pindex) begin
                                        buffer_6 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h6 == pindex) begin
                                          buffer_6 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_6 <= _GEN_135;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_7 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h7 == _T_137) begin
            buffer_7 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h7 == _T_8) begin
                      buffer_7 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h7 == pindex) begin
                            buffer_7 <= 64'h1;
                          end
                        end else begin
                          if (5'h7 == pindex) begin
                            buffer_7 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h7 == pindex) begin
                                buffer_7 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h7 == pindex) begin
                                buffer_7 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h7 == pindex) begin
                                buffer_7 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h7 == pindex) begin
                                  buffer_7 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h7 == pindex) begin
                                    buffer_7 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h7 == pindex) begin
                                      buffer_7 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h7 == pindex) begin
                                        buffer_7 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h7 == pindex) begin
                                          buffer_7 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h7 == pindex) begin
                                            buffer_7 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h7 == pindex) begin
                                              buffer_7 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h7 == pindex) begin
                                  buffer_7 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h7 == _T_8) begin
                    buffer_7 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h7 == pindex) begin
                          buffer_7 <= 64'h1;
                        end
                      end else begin
                        if (5'h7 == pindex) begin
                          buffer_7 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h7 == pindex) begin
                              buffer_7 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h7 == pindex) begin
                              buffer_7 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h7 == pindex) begin
                              buffer_7 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h7 == pindex) begin
                                buffer_7 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h7 == pindex) begin
                                  buffer_7 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h7 == pindex) begin
                                    buffer_7 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h7 == pindex) begin
                                      buffer_7 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h7 == pindex) begin
                                        buffer_7 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h7 == pindex) begin
                                          buffer_7 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h7 == pindex) begin
                                            buffer_7 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h7 == pindex) begin
                                buffer_7 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h7 == _T_8) begin
                  buffer_7 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_7 <= _GEN_136;
                    end else begin
                      if (5'h7 == pindex) begin
                        buffer_7 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h7 == pindex) begin
                            buffer_7 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h7 == pindex) begin
                            buffer_7 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h7 == pindex) begin
                            buffer_7 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h7 == pindex) begin
                              buffer_7 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h7 == pindex) begin
                                buffer_7 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h7 == pindex) begin
                                  buffer_7 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h7 == pindex) begin
                                    buffer_7 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h7 == pindex) begin
                                      buffer_7 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h7 == pindex) begin
                                        buffer_7 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h7 == pindex) begin
                                          buffer_7 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_7 <= _GEN_136;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_8 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h8 == _T_137) begin
            buffer_8 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h8 == _T_8) begin
                      buffer_8 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h8 == pindex) begin
                            buffer_8 <= 64'h1;
                          end
                        end else begin
                          if (5'h8 == pindex) begin
                            buffer_8 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h8 == pindex) begin
                                buffer_8 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h8 == pindex) begin
                                buffer_8 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h8 == pindex) begin
                                buffer_8 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h8 == pindex) begin
                                  buffer_8 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h8 == pindex) begin
                                    buffer_8 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h8 == pindex) begin
                                      buffer_8 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h8 == pindex) begin
                                        buffer_8 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h8 == pindex) begin
                                          buffer_8 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h8 == pindex) begin
                                            buffer_8 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h8 == pindex) begin
                                              buffer_8 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h8 == pindex) begin
                                  buffer_8 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h8 == _T_8) begin
                    buffer_8 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h8 == pindex) begin
                          buffer_8 <= 64'h1;
                        end
                      end else begin
                        if (5'h8 == pindex) begin
                          buffer_8 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h8 == pindex) begin
                              buffer_8 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h8 == pindex) begin
                              buffer_8 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h8 == pindex) begin
                              buffer_8 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h8 == pindex) begin
                                buffer_8 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h8 == pindex) begin
                                  buffer_8 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h8 == pindex) begin
                                    buffer_8 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h8 == pindex) begin
                                      buffer_8 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h8 == pindex) begin
                                        buffer_8 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h8 == pindex) begin
                                          buffer_8 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h8 == pindex) begin
                                            buffer_8 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h8 == pindex) begin
                                buffer_8 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h8 == _T_8) begin
                  buffer_8 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_8 <= _GEN_137;
                    end else begin
                      if (5'h8 == pindex) begin
                        buffer_8 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h8 == pindex) begin
                            buffer_8 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h8 == pindex) begin
                            buffer_8 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h8 == pindex) begin
                            buffer_8 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h8 == pindex) begin
                              buffer_8 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h8 == pindex) begin
                                buffer_8 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h8 == pindex) begin
                                  buffer_8 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h8 == pindex) begin
                                    buffer_8 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h8 == pindex) begin
                                      buffer_8 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h8 == pindex) begin
                                        buffer_8 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h8 == pindex) begin
                                          buffer_8 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_8 <= _GEN_137;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_9 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h9 == _T_137) begin
            buffer_9 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h9 == _T_8) begin
                      buffer_9 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h9 == pindex) begin
                            buffer_9 <= 64'h1;
                          end
                        end else begin
                          if (5'h9 == pindex) begin
                            buffer_9 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h9 == pindex) begin
                                buffer_9 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h9 == pindex) begin
                                buffer_9 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h9 == pindex) begin
                                buffer_9 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h9 == pindex) begin
                                  buffer_9 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h9 == pindex) begin
                                    buffer_9 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h9 == pindex) begin
                                      buffer_9 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h9 == pindex) begin
                                        buffer_9 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h9 == pindex) begin
                                          buffer_9 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h9 == pindex) begin
                                            buffer_9 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h9 == pindex) begin
                                              buffer_9 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h9 == pindex) begin
                                  buffer_9 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h9 == _T_8) begin
                    buffer_9 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h9 == pindex) begin
                          buffer_9 <= 64'h1;
                        end
                      end else begin
                        if (5'h9 == pindex) begin
                          buffer_9 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h9 == pindex) begin
                              buffer_9 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h9 == pindex) begin
                              buffer_9 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h9 == pindex) begin
                              buffer_9 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h9 == pindex) begin
                                buffer_9 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h9 == pindex) begin
                                  buffer_9 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h9 == pindex) begin
                                    buffer_9 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h9 == pindex) begin
                                      buffer_9 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h9 == pindex) begin
                                        buffer_9 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h9 == pindex) begin
                                          buffer_9 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h9 == pindex) begin
                                            buffer_9 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h9 == pindex) begin
                                buffer_9 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h9 == _T_8) begin
                  buffer_9 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_9 <= _GEN_138;
                    end else begin
                      if (5'h9 == pindex) begin
                        buffer_9 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h9 == pindex) begin
                            buffer_9 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h9 == pindex) begin
                            buffer_9 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h9 == pindex) begin
                            buffer_9 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h9 == pindex) begin
                              buffer_9 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h9 == pindex) begin
                                buffer_9 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h9 == pindex) begin
                                  buffer_9 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h9 == pindex) begin
                                    buffer_9 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h9 == pindex) begin
                                      buffer_9 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h9 == pindex) begin
                                        buffer_9 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h9 == pindex) begin
                                          buffer_9 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_9 <= _GEN_138;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_10 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'ha == _T_137) begin
            buffer_10 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'ha == _T_8) begin
                      buffer_10 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'ha == pindex) begin
                            buffer_10 <= 64'h1;
                          end
                        end else begin
                          if (5'ha == pindex) begin
                            buffer_10 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'ha == pindex) begin
                                buffer_10 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'ha == pindex) begin
                                buffer_10 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'ha == pindex) begin
                                buffer_10 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'ha == pindex) begin
                                  buffer_10 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'ha == pindex) begin
                                    buffer_10 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'ha == pindex) begin
                                      buffer_10 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'ha == pindex) begin
                                        buffer_10 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'ha == pindex) begin
                                          buffer_10 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'ha == pindex) begin
                                            buffer_10 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'ha == pindex) begin
                                              buffer_10 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'ha == pindex) begin
                                  buffer_10 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'ha == _T_8) begin
                    buffer_10 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'ha == pindex) begin
                          buffer_10 <= 64'h1;
                        end
                      end else begin
                        if (5'ha == pindex) begin
                          buffer_10 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'ha == pindex) begin
                              buffer_10 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'ha == pindex) begin
                              buffer_10 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'ha == pindex) begin
                              buffer_10 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'ha == pindex) begin
                                buffer_10 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'ha == pindex) begin
                                  buffer_10 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'ha == pindex) begin
                                    buffer_10 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'ha == pindex) begin
                                      buffer_10 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'ha == pindex) begin
                                        buffer_10 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'ha == pindex) begin
                                          buffer_10 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'ha == pindex) begin
                                            buffer_10 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'ha == pindex) begin
                                buffer_10 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'ha == _T_8) begin
                  buffer_10 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_10 <= _GEN_139;
                    end else begin
                      if (5'ha == pindex) begin
                        buffer_10 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'ha == pindex) begin
                            buffer_10 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'ha == pindex) begin
                            buffer_10 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'ha == pindex) begin
                            buffer_10 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'ha == pindex) begin
                              buffer_10 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'ha == pindex) begin
                                buffer_10 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'ha == pindex) begin
                                  buffer_10 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'ha == pindex) begin
                                    buffer_10 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'ha == pindex) begin
                                      buffer_10 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'ha == pindex) begin
                                        buffer_10 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'ha == pindex) begin
                                          buffer_10 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_10 <= _GEN_139;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_11 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'hb == _T_137) begin
            buffer_11 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'hb == _T_8) begin
                      buffer_11 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'hb == pindex) begin
                            buffer_11 <= 64'h1;
                          end
                        end else begin
                          if (5'hb == pindex) begin
                            buffer_11 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'hb == pindex) begin
                                buffer_11 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'hb == pindex) begin
                                buffer_11 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'hb == pindex) begin
                                buffer_11 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'hb == pindex) begin
                                  buffer_11 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'hb == pindex) begin
                                    buffer_11 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'hb == pindex) begin
                                      buffer_11 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'hb == pindex) begin
                                        buffer_11 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'hb == pindex) begin
                                          buffer_11 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'hb == pindex) begin
                                            buffer_11 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'hb == pindex) begin
                                              buffer_11 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'hb == pindex) begin
                                  buffer_11 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'hb == _T_8) begin
                    buffer_11 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'hb == pindex) begin
                          buffer_11 <= 64'h1;
                        end
                      end else begin
                        if (5'hb == pindex) begin
                          buffer_11 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'hb == pindex) begin
                              buffer_11 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'hb == pindex) begin
                              buffer_11 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'hb == pindex) begin
                              buffer_11 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'hb == pindex) begin
                                buffer_11 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'hb == pindex) begin
                                  buffer_11 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'hb == pindex) begin
                                    buffer_11 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'hb == pindex) begin
                                      buffer_11 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'hb == pindex) begin
                                        buffer_11 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'hb == pindex) begin
                                          buffer_11 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'hb == pindex) begin
                                            buffer_11 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'hb == pindex) begin
                                buffer_11 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'hb == _T_8) begin
                  buffer_11 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_11 <= _GEN_140;
                    end else begin
                      if (5'hb == pindex) begin
                        buffer_11 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'hb == pindex) begin
                            buffer_11 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'hb == pindex) begin
                            buffer_11 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'hb == pindex) begin
                            buffer_11 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'hb == pindex) begin
                              buffer_11 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'hb == pindex) begin
                                buffer_11 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'hb == pindex) begin
                                  buffer_11 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'hb == pindex) begin
                                    buffer_11 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'hb == pindex) begin
                                      buffer_11 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'hb == pindex) begin
                                        buffer_11 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'hb == pindex) begin
                                          buffer_11 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_11 <= _GEN_140;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_12 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'hc == _T_137) begin
            buffer_12 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'hc == _T_8) begin
                      buffer_12 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'hc == pindex) begin
                            buffer_12 <= 64'h1;
                          end
                        end else begin
                          if (5'hc == pindex) begin
                            buffer_12 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'hc == pindex) begin
                                buffer_12 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'hc == pindex) begin
                                buffer_12 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'hc == pindex) begin
                                buffer_12 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'hc == pindex) begin
                                  buffer_12 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'hc == pindex) begin
                                    buffer_12 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'hc == pindex) begin
                                      buffer_12 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'hc == pindex) begin
                                        buffer_12 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'hc == pindex) begin
                                          buffer_12 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'hc == pindex) begin
                                            buffer_12 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'hc == pindex) begin
                                              buffer_12 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'hc == pindex) begin
                                  buffer_12 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'hc == _T_8) begin
                    buffer_12 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'hc == pindex) begin
                          buffer_12 <= 64'h1;
                        end
                      end else begin
                        if (5'hc == pindex) begin
                          buffer_12 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'hc == pindex) begin
                              buffer_12 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'hc == pindex) begin
                              buffer_12 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'hc == pindex) begin
                              buffer_12 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'hc == pindex) begin
                                buffer_12 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'hc == pindex) begin
                                  buffer_12 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'hc == pindex) begin
                                    buffer_12 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'hc == pindex) begin
                                      buffer_12 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'hc == pindex) begin
                                        buffer_12 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'hc == pindex) begin
                                          buffer_12 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'hc == pindex) begin
                                            buffer_12 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'hc == pindex) begin
                                buffer_12 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'hc == _T_8) begin
                  buffer_12 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_12 <= _GEN_141;
                    end else begin
                      if (5'hc == pindex) begin
                        buffer_12 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'hc == pindex) begin
                            buffer_12 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'hc == pindex) begin
                            buffer_12 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'hc == pindex) begin
                            buffer_12 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'hc == pindex) begin
                              buffer_12 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'hc == pindex) begin
                                buffer_12 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'hc == pindex) begin
                                  buffer_12 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'hc == pindex) begin
                                    buffer_12 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'hc == pindex) begin
                                      buffer_12 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'hc == pindex) begin
                                        buffer_12 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'hc == pindex) begin
                                          buffer_12 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_12 <= _GEN_141;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_13 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'hd == _T_137) begin
            buffer_13 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'hd == _T_8) begin
                      buffer_13 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'hd == pindex) begin
                            buffer_13 <= 64'h1;
                          end
                        end else begin
                          if (5'hd == pindex) begin
                            buffer_13 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'hd == pindex) begin
                                buffer_13 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'hd == pindex) begin
                                buffer_13 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'hd == pindex) begin
                                buffer_13 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'hd == pindex) begin
                                  buffer_13 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'hd == pindex) begin
                                    buffer_13 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'hd == pindex) begin
                                      buffer_13 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'hd == pindex) begin
                                        buffer_13 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'hd == pindex) begin
                                          buffer_13 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'hd == pindex) begin
                                            buffer_13 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'hd == pindex) begin
                                              buffer_13 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'hd == pindex) begin
                                  buffer_13 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'hd == _T_8) begin
                    buffer_13 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'hd == pindex) begin
                          buffer_13 <= 64'h1;
                        end
                      end else begin
                        if (5'hd == pindex) begin
                          buffer_13 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'hd == pindex) begin
                              buffer_13 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'hd == pindex) begin
                              buffer_13 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'hd == pindex) begin
                              buffer_13 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'hd == pindex) begin
                                buffer_13 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'hd == pindex) begin
                                  buffer_13 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'hd == pindex) begin
                                    buffer_13 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'hd == pindex) begin
                                      buffer_13 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'hd == pindex) begin
                                        buffer_13 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'hd == pindex) begin
                                          buffer_13 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'hd == pindex) begin
                                            buffer_13 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'hd == pindex) begin
                                buffer_13 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'hd == _T_8) begin
                  buffer_13 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_13 <= _GEN_142;
                    end else begin
                      if (5'hd == pindex) begin
                        buffer_13 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'hd == pindex) begin
                            buffer_13 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'hd == pindex) begin
                            buffer_13 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'hd == pindex) begin
                            buffer_13 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'hd == pindex) begin
                              buffer_13 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'hd == pindex) begin
                                buffer_13 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'hd == pindex) begin
                                  buffer_13 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'hd == pindex) begin
                                    buffer_13 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'hd == pindex) begin
                                      buffer_13 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'hd == pindex) begin
                                        buffer_13 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'hd == pindex) begin
                                          buffer_13 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_13 <= _GEN_142;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_14 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'he == _T_137) begin
            buffer_14 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'he == _T_8) begin
                      buffer_14 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'he == pindex) begin
                            buffer_14 <= 64'h1;
                          end
                        end else begin
                          if (5'he == pindex) begin
                            buffer_14 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'he == pindex) begin
                                buffer_14 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'he == pindex) begin
                                buffer_14 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'he == pindex) begin
                                buffer_14 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'he == pindex) begin
                                  buffer_14 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'he == pindex) begin
                                    buffer_14 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'he == pindex) begin
                                      buffer_14 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'he == pindex) begin
                                        buffer_14 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'he == pindex) begin
                                          buffer_14 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'he == pindex) begin
                                            buffer_14 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'he == pindex) begin
                                              buffer_14 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'he == pindex) begin
                                  buffer_14 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'he == _T_8) begin
                    buffer_14 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'he == pindex) begin
                          buffer_14 <= 64'h1;
                        end
                      end else begin
                        if (5'he == pindex) begin
                          buffer_14 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'he == pindex) begin
                              buffer_14 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'he == pindex) begin
                              buffer_14 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'he == pindex) begin
                              buffer_14 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'he == pindex) begin
                                buffer_14 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'he == pindex) begin
                                  buffer_14 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'he == pindex) begin
                                    buffer_14 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'he == pindex) begin
                                      buffer_14 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'he == pindex) begin
                                        buffer_14 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'he == pindex) begin
                                          buffer_14 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'he == pindex) begin
                                            buffer_14 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'he == pindex) begin
                                buffer_14 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'he == _T_8) begin
                  buffer_14 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_14 <= _GEN_143;
                    end else begin
                      if (5'he == pindex) begin
                        buffer_14 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'he == pindex) begin
                            buffer_14 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'he == pindex) begin
                            buffer_14 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'he == pindex) begin
                            buffer_14 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'he == pindex) begin
                              buffer_14 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'he == pindex) begin
                                buffer_14 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'he == pindex) begin
                                  buffer_14 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'he == pindex) begin
                                    buffer_14 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'he == pindex) begin
                                      buffer_14 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'he == pindex) begin
                                        buffer_14 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'he == pindex) begin
                                          buffer_14 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_14 <= _GEN_143;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_15 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'hf == _T_137) begin
            buffer_15 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'hf == _T_8) begin
                      buffer_15 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'hf == pindex) begin
                            buffer_15 <= 64'h1;
                          end
                        end else begin
                          if (5'hf == pindex) begin
                            buffer_15 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'hf == pindex) begin
                                buffer_15 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'hf == pindex) begin
                                buffer_15 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'hf == pindex) begin
                                buffer_15 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'hf == pindex) begin
                                  buffer_15 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'hf == pindex) begin
                                    buffer_15 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'hf == pindex) begin
                                      buffer_15 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'hf == pindex) begin
                                        buffer_15 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'hf == pindex) begin
                                          buffer_15 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'hf == pindex) begin
                                            buffer_15 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'hf == pindex) begin
                                              buffer_15 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'hf == pindex) begin
                                  buffer_15 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'hf == _T_8) begin
                    buffer_15 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'hf == pindex) begin
                          buffer_15 <= 64'h1;
                        end
                      end else begin
                        if (5'hf == pindex) begin
                          buffer_15 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'hf == pindex) begin
                              buffer_15 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'hf == pindex) begin
                              buffer_15 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'hf == pindex) begin
                              buffer_15 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'hf == pindex) begin
                                buffer_15 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'hf == pindex) begin
                                  buffer_15 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'hf == pindex) begin
                                    buffer_15 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'hf == pindex) begin
                                      buffer_15 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'hf == pindex) begin
                                        buffer_15 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'hf == pindex) begin
                                          buffer_15 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'hf == pindex) begin
                                            buffer_15 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'hf == pindex) begin
                                buffer_15 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'hf == _T_8) begin
                  buffer_15 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_15 <= _GEN_144;
                    end else begin
                      if (5'hf == pindex) begin
                        buffer_15 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'hf == pindex) begin
                            buffer_15 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'hf == pindex) begin
                            buffer_15 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'hf == pindex) begin
                            buffer_15 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'hf == pindex) begin
                              buffer_15 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'hf == pindex) begin
                                buffer_15 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'hf == pindex) begin
                                  buffer_15 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'hf == pindex) begin
                                    buffer_15 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'hf == pindex) begin
                                      buffer_15 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'hf == pindex) begin
                                        buffer_15 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'hf == pindex) begin
                                          buffer_15 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_15 <= _GEN_144;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_16 <= 64'h0;
    end else begin
      if (io_dmem_resp_val) begin
        if (_T_138) begin
          if (5'h10 == _T_137) begin
            buffer_16 <= io_dmem_resp_data;
          end else begin
            if (!(_T_15)) begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (5'h10 == _T_8) begin
                      buffer_16 <= io_dmem_resp_data;
                    end
                  end
                end else begin
                  if (_T_77) begin
                    if (_T_81) begin
                      if (_T_84) begin
                        if (_T_91) begin
                          if (5'h10 == pindex) begin
                            buffer_16 <= 64'h1;
                          end
                        end else begin
                          if (5'h10 == pindex) begin
                            buffer_16 <= 64'h0;
                          end
                        end
                      end else begin
                        if (_T_93) begin
                          if (_T_94) begin
                            if (_T_95) begin
                              if (5'h10 == pindex) begin
                                buffer_16 <= _T_97;
                              end
                            end
                          end else begin
                            if (_T_99) begin
                              if (5'h10 == pindex) begin
                                buffer_16 <= 64'h8000000000000000;
                              end
                            end else begin
                              if (5'h10 == pindex) begin
                                buffer_16 <= _T_102;
                              end
                            end
                          end
                        end else begin
                          if (_T_103) begin
                            if (_T_104) begin
                              if (_T_105) begin
                                if (5'h10 == pindex) begin
                                  buffer_16 <= _buffer_pindex_7;
                                end
                              end else begin
                                if (_T_108) begin
                                  if (5'h10 == pindex) begin
                                    buffer_16 <= _buffer_pindex_9;
                                  end
                                end else begin
                                  if (_T_111) begin
                                    if (5'h10 == pindex) begin
                                      buffer_16 <= _buffer_pindex_11;
                                    end
                                  end else begin
                                    if (_T_114) begin
                                      if (5'h10 == pindex) begin
                                        buffer_16 <= _buffer_pindex_13;
                                      end
                                    end else begin
                                      if (_T_117) begin
                                        if (5'h10 == pindex) begin
                                          buffer_16 <= _buffer_pindex_15;
                                        end
                                      end else begin
                                        if (_T_120) begin
                                          if (5'h10 == pindex) begin
                                            buffer_16 <= _buffer_pindex_17;
                                          end
                                        end else begin
                                          if (_T_95) begin
                                            if (5'h10 == pindex) begin
                                              buffer_16 <= _buffer_pindex_19;
                                            end
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end else begin
                              if (_T_128) begin
                                if (5'h10 == pindex) begin
                                  buffer_16 <= 64'h1;
                                end
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (!(_T_15)) begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (5'h10 == _T_8) begin
                    buffer_16 <= io_dmem_resp_data;
                  end
                end
              end else begin
                if (_T_77) begin
                  if (_T_81) begin
                    if (_T_84) begin
                      if (_T_91) begin
                        if (5'h10 == pindex) begin
                          buffer_16 <= 64'h1;
                        end
                      end else begin
                        if (5'h10 == pindex) begin
                          buffer_16 <= 64'h0;
                        end
                      end
                    end else begin
                      if (_T_93) begin
                        if (_T_94) begin
                          if (_T_95) begin
                            if (5'h10 == pindex) begin
                              buffer_16 <= _T_97;
                            end
                          end
                        end else begin
                          if (_T_99) begin
                            if (5'h10 == pindex) begin
                              buffer_16 <= 64'h8000000000000000;
                            end
                          end else begin
                            if (5'h10 == pindex) begin
                              buffer_16 <= _T_102;
                            end
                          end
                        end
                      end else begin
                        if (_T_103) begin
                          if (_T_104) begin
                            if (_T_105) begin
                              if (5'h10 == pindex) begin
                                buffer_16 <= _buffer_pindex_7;
                              end
                            end else begin
                              if (_T_108) begin
                                if (5'h10 == pindex) begin
                                  buffer_16 <= _buffer_pindex_9;
                                end
                              end else begin
                                if (_T_111) begin
                                  if (5'h10 == pindex) begin
                                    buffer_16 <= _buffer_pindex_11;
                                  end
                                end else begin
                                  if (_T_114) begin
                                    if (5'h10 == pindex) begin
                                      buffer_16 <= _buffer_pindex_13;
                                    end
                                  end else begin
                                    if (_T_117) begin
                                      if (5'h10 == pindex) begin
                                        buffer_16 <= _buffer_pindex_15;
                                      end
                                    end else begin
                                      if (_T_120) begin
                                        if (5'h10 == pindex) begin
                                          buffer_16 <= _buffer_pindex_17;
                                        end
                                      end else begin
                                        if (_T_95) begin
                                          if (5'h10 == pindex) begin
                                            buffer_16 <= _buffer_pindex_19;
                                          end
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end else begin
                            if (_T_128) begin
                              if (5'h10 == pindex) begin
                                buffer_16 <= 64'h1;
                              end
                            end
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (!(_T_15)) begin
          if (!(_T_26)) begin
            if (_T_58) begin
              if (io_dmem_resp_val) begin
                if (5'h10 == _T_8) begin
                  buffer_16 <= io_dmem_resp_data;
                end
              end
            end else begin
              if (_T_77) begin
                if (_T_81) begin
                  if (_T_84) begin
                    if (_T_91) begin
                      buffer_16 <= _GEN_145;
                    end else begin
                      if (5'h10 == pindex) begin
                        buffer_16 <= 64'h0;
                      end
                    end
                  end else begin
                    if (_T_93) begin
                      if (_T_94) begin
                        if (_T_95) begin
                          if (5'h10 == pindex) begin
                            buffer_16 <= _T_97;
                          end
                        end
                      end else begin
                        if (_T_99) begin
                          if (5'h10 == pindex) begin
                            buffer_16 <= 64'h8000000000000000;
                          end
                        end else begin
                          if (5'h10 == pindex) begin
                            buffer_16 <= _T_102;
                          end
                        end
                      end
                    end else begin
                      if (_T_103) begin
                        if (_T_104) begin
                          if (_T_105) begin
                            if (5'h10 == pindex) begin
                              buffer_16 <= _buffer_pindex_7;
                            end
                          end else begin
                            if (_T_108) begin
                              if (5'h10 == pindex) begin
                                buffer_16 <= _buffer_pindex_9;
                              end
                            end else begin
                              if (_T_111) begin
                                if (5'h10 == pindex) begin
                                  buffer_16 <= _buffer_pindex_11;
                                end
                              end else begin
                                if (_T_114) begin
                                  if (5'h10 == pindex) begin
                                    buffer_16 <= _buffer_pindex_13;
                                  end
                                end else begin
                                  if (_T_117) begin
                                    if (5'h10 == pindex) begin
                                      buffer_16 <= _buffer_pindex_15;
                                    end
                                  end else begin
                                    if (_T_120) begin
                                      if (5'h10 == pindex) begin
                                        buffer_16 <= _buffer_pindex_17;
                                      end
                                    end else begin
                                      if (_T_95) begin
                                        if (5'h10 == pindex) begin
                                          buffer_16 <= _buffer_pindex_19;
                                        end
                                      end
                                    end
                                  end
                                end
                              end
                            end
                          end
                        end else begin
                          if (_T_128) begin
                            buffer_16 <= _GEN_145;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_valid <= 1'h0;
    end else begin
      if (_T_146) begin
        if (_T_144) begin
          if (_T_145) begin
            if (!(_T_15)) begin
              if (_T_26) begin
                if (_T_40) begin
                  buffer_valid <= _GEN_55;
                end else begin
                  if (!(_T_48)) begin
                    if (_T_57) begin
                      buffer_valid <= 1'h0;
                    end
                  end
                end
              end else begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    if (_T_40) begin
                      buffer_valid <= _GEN_55;
                    end else begin
                      if (!(_T_48)) begin
                        if (_T_57) begin
                          buffer_valid <= 1'h0;
                        end else begin
                          buffer_valid <= 1'h1;
                        end
                      end
                    end
                  end
                end else begin
                  if (_T_77) begin
                    buffer_valid <= next_buff_val;
                  end else begin
                    buffer_valid <= _GEN_668;
                  end
                end
              end
            end
          end else begin
            buffer_valid <= 1'h1;
          end
        end else begin
          if (!(_T_15)) begin
            if (_T_26) begin
              if (_T_40) begin
                buffer_valid <= _GEN_55;
              end else begin
                if (!(_T_48)) begin
                  if (_T_57) begin
                    buffer_valid <= 1'h0;
                  end
                end
              end
            end else begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  if (_T_40) begin
                    buffer_valid <= _GEN_55;
                  end else begin
                    if (!(_T_48)) begin
                      if (_T_57) begin
                        buffer_valid <= 1'h0;
                      end else begin
                        buffer_valid <= 1'h1;
                      end
                    end
                  end
                end
              end else begin
                if (_T_77) begin
                  buffer_valid <= next_buff_val;
                end else begin
                  buffer_valid <= _GEN_668;
                end
              end
            end
          end
        end
      end else begin
        if (_T_152) begin
          if (_T_156) begin
            buffer_valid <= 1'h0;
          end else begin
            if (_T_144) begin
              if (_T_145) begin
                if (!(_T_15)) begin
                  if (_T_26) begin
                    if (_T_40) begin
                      buffer_valid <= _GEN_55;
                    end else begin
                      if (!(_T_48)) begin
                        if (_T_57) begin
                          buffer_valid <= 1'h0;
                        end
                      end
                    end
                  end else begin
                    if (_T_58) begin
                      if (io_dmem_resp_val) begin
                        if (_T_40) begin
                          buffer_valid <= _GEN_55;
                        end else begin
                          if (!(_T_48)) begin
                            if (_T_57) begin
                              buffer_valid <= 1'h0;
                            end else begin
                              buffer_valid <= 1'h1;
                            end
                          end
                        end
                      end
                    end else begin
                      if (_T_77) begin
                        buffer_valid <= next_buff_val;
                      end else begin
                        buffer_valid <= _GEN_668;
                      end
                    end
                  end
                end
              end else begin
                buffer_valid <= 1'h1;
              end
            end else begin
              if (!(_T_15)) begin
                if (_T_26) begin
                  if (_T_40) begin
                    buffer_valid <= _GEN_55;
                  end else begin
                    if (!(_T_48)) begin
                      if (_T_57) begin
                        buffer_valid <= 1'h0;
                      end
                    end
                  end
                end else begin
                  if (_T_58) begin
                    if (io_dmem_resp_val) begin
                      if (_T_40) begin
                        buffer_valid <= _GEN_55;
                      end else begin
                        if (!(_T_48)) begin
                          if (_T_57) begin
                            buffer_valid <= 1'h0;
                          end else begin
                            buffer_valid <= 1'h1;
                          end
                        end
                      end
                    end
                  end else begin
                    if (_T_77) begin
                      buffer_valid <= next_buff_val;
                    end else begin
                      buffer_valid <= _GEN_668;
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (_T_159) begin
            if (_T_144) begin
              if (_T_145) begin
                buffer_valid <= _GEN_756;
              end else begin
                buffer_valid <= 1'h1;
              end
            end else begin
              buffer_valid <= _GEN_756;
            end
          end else begin
            if (_T_160) begin
              if (_T_144) begin
                if (_T_145) begin
                  buffer_valid <= _GEN_756;
                end else begin
                  buffer_valid <= 1'h1;
                end
              end else begin
                buffer_valid <= _GEN_756;
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  buffer_valid <= 1'h0;
                end else begin
                  buffer_valid <= _GEN_831;
                end
              end else begin
                buffer_valid <= _GEN_831;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      buffer_count <= 5'h0;
    end else begin
      if (_T_146) begin
        if (io_dmem_resp_val) begin
          if (_T_138) begin
            buffer_count <= _T_62;
          end else begin
            if (_T_15) begin
              if (_T_25) begin
                buffer_count <= 5'h0;
              end
            end else begin
              if (!(_T_26)) begin
                if (_T_58) begin
                  if (io_dmem_resp_val) begin
                    buffer_count <= _T_62;
                  end
                end else begin
                  if (_T_77) begin
                    if (next_buff_val) begin
                      if (areg) begin
                        buffer_count <= 5'h0;
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (_T_15) begin
            if (_T_25) begin
              buffer_count <= 5'h0;
            end
          end else begin
            if (!(_T_26)) begin
              if (_T_58) begin
                if (io_dmem_resp_val) begin
                  buffer_count <= _T_62;
                end
              end else begin
                if (_T_77) begin
                  if (next_buff_val) begin
                    if (areg) begin
                      buffer_count <= 5'h0;
                    end
                  end
                end
              end
            end
          end
        end
      end else begin
        if (_T_152) begin
          if (_T_156) begin
            buffer_count <= 5'h0;
          end else begin
            if (io_dmem_resp_val) begin
              if (_T_138) begin
                buffer_count <= _T_62;
              end else begin
                if (_T_15) begin
                  if (_T_25) begin
                    buffer_count <= 5'h0;
                  end
                end else begin
                  if (!(_T_26)) begin
                    if (_T_58) begin
                      if (io_dmem_resp_val) begin
                        buffer_count <= _T_62;
                      end
                    end else begin
                      if (_T_77) begin
                        if (next_buff_val) begin
                          if (areg) begin
                            buffer_count <= 5'h0;
                          end
                        end
                      end
                    end
                  end
                end
              end
            end else begin
              if (_T_15) begin
                if (_T_25) begin
                  buffer_count <= 5'h0;
                end
              end else begin
                if (!(_T_26)) begin
                  if (_T_58) begin
                    if (io_dmem_resp_val) begin
                      buffer_count <= _T_62;
                    end
                  end else begin
                    if (_T_77) begin
                      if (next_buff_val) begin
                        if (areg) begin
                          buffer_count <= 5'h0;
                        end
                      end
                    end
                  end
                end
              end
            end
          end
        end else begin
          if (_T_159) begin
            if (io_dmem_resp_val) begin
              if (_T_138) begin
                buffer_count <= _T_62;
              end else begin
                buffer_count <= _GEN_747;
              end
            end else begin
              buffer_count <= _GEN_747;
            end
          end else begin
            if (_T_160) begin
              if (io_dmem_resp_val) begin
                if (_T_138) begin
                  buffer_count <= _T_62;
                end else begin
                  buffer_count <= _GEN_747;
                end
              end else begin
                buffer_count <= _GEN_747;
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  buffer_count <= 5'h0;
                end else begin
                  buffer_count <= _GEN_829;
                end
              end else begin
                buffer_count <= _GEN_829;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      read <= 32'h0;
    end else begin
      if (_T_146) begin
        if (!(_T_15)) begin
          if (_T_26) begin
            if (_T_40) begin
              if (_T_27) begin
                if (_T_18) begin
                  read <= 32'h1;
                end else begin
                  if (_T_34) begin
                    read <= _T_38;
                  end
                end
              end
            end else begin
              if (_T_48) begin
                if (_T_27) begin
                  if (_T_18) begin
                    read <= 32'h1;
                  end else begin
                    if (_T_34) begin
                      read <= _T_38;
                    end
                  end
                end
              end else begin
                read <= _T_38;
              end
            end
          end
        end
      end else begin
        if (_T_152) begin
          if (!(_T_15)) begin
            if (_T_26) begin
              if (_T_40) begin
                if (_T_27) begin
                  if (_T_18) begin
                    read <= 32'h1;
                  end else begin
                    if (_T_34) begin
                      read <= _T_38;
                    end
                  end
                end
              end else begin
                if (_T_48) begin
                  if (_T_27) begin
                    if (_T_18) begin
                      read <= 32'h1;
                    end else begin
                      if (_T_34) begin
                        read <= _T_38;
                      end
                    end
                  end
                end else begin
                  read <= _T_38;
                end
              end
            end
          end
        end else begin
          if (_T_159) begin
            if (!(_T_15)) begin
              if (_T_26) begin
                if (_T_40) begin
                  read <= _GEN_51;
                end else begin
                  if (_T_48) begin
                    read <= _GEN_51;
                  end else begin
                    read <= _T_38;
                  end
                end
              end
            end
          end else begin
            if (_T_160) begin
              if (!(_T_15)) begin
                if (_T_26) begin
                  if (_T_40) begin
                    read <= _GEN_51;
                  end else begin
                    if (_T_48) begin
                      read <= _GEN_51;
                    end else begin
                      read <= _T_38;
                    end
                  end
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  read <= 32'h0;
                end else begin
                  read <= _GEN_755;
                end
              end else begin
                read <= _GEN_755;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      hashed <= 32'h0;
    end else begin
      if (!(_T_146)) begin
        if (_T_152) begin
          if (_T_156) begin
            hashed <= _T_158;
          end
        end else begin
          if (!(_T_159)) begin
            if (!(_T_160)) begin
              if (_T_172) begin
                if (_T_189) begin
                  hashed <= 32'h0;
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      areg <= 1'h0;
    end else begin
      if (_T_146) begin
        areg <= 1'h0;
      end else begin
        areg <= _T_152;
      end
    end
    if (reset) begin
      mindex <= 5'h0;
    end else begin
      if (_T_15) begin
        if (_T_25) begin
          mindex <= 5'h0;
        end
      end else begin
        if (_T_26) begin
          if (_T_40) begin
            if (_T_27) begin
              if (_T_34) begin
                mindex <= _T_36;
              end
            end
          end else begin
            if (_T_48) begin
              if (_T_27) begin
                if (_T_34) begin
                  mindex <= _T_36;
                end
              end
            end else begin
              mindex <= _T_36;
            end
          end
        end else begin
          if (!(_T_58)) begin
            if (_T_77) begin
              if (next_buff_val) begin
                mindex <= 5'h0;
              end
            end
          end
        end
      end
    end
    if (reset) begin
      windex <= 3'h0;
    end else begin
      if (!(_T_146)) begin
        if (!(_T_152)) begin
          if (!(_T_159)) begin
            if (_T_160) begin
              if (!(_T_161)) begin
                if (_T_171) begin
                  windex <= 3'h0;
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  windex <= 3'h4;
                end else begin
                  if (io_dmem_req_rdy) begin
                    windex <= _T_180;
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      aindex <= 5'h0;
    end else begin
      if (!(_T_146)) begin
        if (_T_152) begin
          if (_T_156) begin
            aindex <= 5'h0;
          end else begin
            aindex <= _T_155;
          end
        end
      end
    end
    if (reset) begin
      pindex <= 5'h0;
    end else begin
      if (!(_T_15)) begin
        if (_T_26) begin
          if (_T_40) begin
            if (_T_41) begin
              if (_T_44) begin
                if (_T_6) begin
                  pindex <= _T_8;
                end else begin
                  pindex <= mindex;
                end
              end
            end else begin
              if (_T_6) begin
                pindex <= _T_8;
              end else begin
                pindex <= mindex;
              end
            end
          end else begin
            if (!(_T_48)) begin
              if (_T_57) begin
                if (_T_6) begin
                  pindex <= _T_8;
                end else begin
                  pindex <= mindex;
                end
              end
            end
          end
        end else begin
          if (_T_58) begin
            if (io_dmem_resp_val) begin
              if (_T_40) begin
                if (_T_41) begin
                  if (_T_44) begin
                    if (_T_6) begin
                      pindex <= _T_8;
                    end else begin
                      pindex <= mindex;
                    end
                  end
                end else begin
                  pindex <= words_filled;
                end
              end else begin
                if (!(_T_48)) begin
                  if (_T_57) begin
                    pindex <= words_filled;
                  end
                end
              end
            end
          end else begin
            if (_T_77) begin
              if (next_buff_val) begin
                pindex <= 5'h0;
              end else begin
                if (!(_T_80)) begin
                  pindex <= _T_134;
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      writes_done_0 <= 1'h0;
    end else begin
      if (!(_T_146)) begin
        if (!(_T_152)) begin
          if (!(_T_159)) begin
            if (!(_T_160)) begin
              if (_T_172) begin
                if (_T_189) begin
                  writes_done_0 <= 1'h0;
                end else begin
                  if (io_dmem_resp_val) begin
                    if (_T_182) begin
                      writes_done_0 <= _GEN_857;
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      writes_done_1 <= 1'h0;
    end else begin
      if (!(_T_146)) begin
        if (!(_T_152)) begin
          if (!(_T_159)) begin
            if (!(_T_160)) begin
              if (_T_172) begin
                if (_T_189) begin
                  writes_done_1 <= 1'h0;
                end else begin
                  if (io_dmem_resp_val) begin
                    if (_T_182) begin
                      writes_done_1 <= _GEN_858;
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      writes_done_2 <= 1'h0;
    end else begin
      if (!(_T_146)) begin
        if (!(_T_152)) begin
          if (!(_T_159)) begin
            if (!(_T_160)) begin
              if (_T_172) begin
                if (_T_189) begin
                  writes_done_2 <= 1'h0;
                end else begin
                  if (io_dmem_resp_val) begin
                    if (_T_182) begin
                      writes_done_2 <= _GEN_859;
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      writes_done_3 <= 1'h0;
    end else begin
      if (!(_T_146)) begin
        if (!(_T_152)) begin
          if (!(_T_159)) begin
            if (!(_T_160)) begin
              if (_T_172) begin
                if (_T_189) begin
                  writes_done_3 <= 1'h0;
                end else begin
                  if (io_dmem_resp_val) begin
                    if (_T_182) begin
                      writes_done_3 <= _GEN_860;
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      next_buff_val <= 1'h0;
    end else begin
      next_buff_val <= _T_4;
    end
    _T_5 <= aindex;
    if (reset) begin
      state <= 3'h0;
    end else begin
      if (_T_146) begin
        if (_T_151) begin
          state <= 3'h1;
        end else begin
          state <= 3'h0;
        end
      end else begin
        if (_T_152) begin
          if (_T_156) begin
            state <= 3'h2;
          end else begin
            state <= 3'h1;
          end
        end else begin
          if (_T_159) begin
            state <= 3'h3;
          end else begin
            if (_T_160) begin
              if (_T_161) begin
                state <= 3'h3;
              end else begin
                if (_T_171) begin
                  state <= 3'h4;
                end else begin
                  state <= 3'h0;
                end
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  state <= 3'h0;
                end else begin
                  state <= 3'h4;
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      rindex <= 5'h19;
    end else begin
      if (!(_T_146)) begin
        if (_T_152) begin
          if (_T_156) begin
            rindex <= 5'h0;
          end
        end else begin
          if (!(_T_159)) begin
            if (_T_160) begin
              if (_T_161) begin
                rindex <= _T_166;
              end
            end else begin
              if (_T_172) begin
                if (_T_189) begin
                  rindex <= 5'h19;
                end
              end
            end
          end
        end
      end
    end
    rindex_reg <= rindex;
  end
endmodule
module ThetaModule( // @[:example.TestHarness.Sha3RocketConfig.fir@135213.2]
  input  [63:0] io_state_i_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  input  [63:0] io_state_i_24, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
  output [63:0] io_state_o_24 // @[:example.TestHarness.Sha3RocketConfig.fir@135216.4]
);
  wire [63:0] _T_5; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135238.4]
  wire [63:0] _T_6; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135239.4]
  wire [63:0] _T_7; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135240.4]
  wire [63:0] bc_0; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135241.4]
  wire [63:0] _T_9; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135243.4]
  wire [63:0] _T_10; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135244.4]
  wire [63:0] _T_11; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135245.4]
  wire [63:0] bc_1; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135246.4]
  wire [63:0] _T_13; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135248.4]
  wire [63:0] _T_14; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135249.4]
  wire [63:0] _T_15; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135250.4]
  wire [63:0] bc_2; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135251.4]
  wire [63:0] _T_17; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135253.4]
  wire [63:0] _T_18; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135254.4]
  wire [63:0] _T_19; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135255.4]
  wire [63:0] bc_3; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135256.4]
  wire [63:0] _T_21; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135258.4]
  wire [63:0] _T_22; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135259.4]
  wire [63:0] _T_23; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135260.4]
  wire [63:0] bc_4; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135261.4]
  wire [64:0] _T_26; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135265.4]
  wire [6:0] _T_28; // @[common.scala 24:68:example.TestHarness.Sha3RocketConfig.fir@135267.4]
  wire [63:0] _T_29; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135268.4]
  wire [64:0] _GEN_0; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135269.4]
  wire [64:0] _T_30; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135269.4]
  wire [64:0] _GEN_1; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135270.4]
  wire [64:0] _T_31; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135270.4]
  wire [63:0] _T_25; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135263.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135271.4]
  wire [64:0] _T_38; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135284.4]
  wire [63:0] _T_41; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135287.4]
  wire [64:0] _GEN_2; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135288.4]
  wire [64:0] _T_42; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135288.4]
  wire [64:0] _GEN_3; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135289.4]
  wire [64:0] _T_43; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135289.4]
  wire [63:0] _T_37; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135282.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135290.4]
  wire [64:0] _T_50; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135303.4]
  wire [63:0] _T_53; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135306.4]
  wire [64:0] _GEN_4; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135307.4]
  wire [64:0] _T_54; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135307.4]
  wire [64:0] _GEN_5; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135308.4]
  wire [64:0] _T_55; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135308.4]
  wire [63:0] _T_49; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135301.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135309.4]
  wire [64:0] _T_62; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135322.4]
  wire [63:0] _T_65; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135325.4]
  wire [64:0] _GEN_6; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135326.4]
  wire [64:0] _T_66; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135326.4]
  wire [64:0] _GEN_7; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135327.4]
  wire [64:0] _T_67; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135327.4]
  wire [63:0] _T_61; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135320.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135328.4]
  wire [64:0] _T_74; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135341.4]
  wire [63:0] _T_77; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135344.4]
  wire [64:0] _GEN_8; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135345.4]
  wire [64:0] _T_78; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135345.4]
  wire [64:0] _GEN_9; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135346.4]
  wire [64:0] _T_79; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135346.4]
  wire [63:0] _T_73; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135339.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135347.4]
  assign _T_5 = io_state_i_0 ^ io_state_i_1; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135238.4]
  assign _T_6 = _T_5 ^ io_state_i_2; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135239.4]
  assign _T_7 = _T_6 ^ io_state_i_3; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135240.4]
  assign bc_0 = _T_7 ^ io_state_i_4; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135241.4]
  assign _T_9 = io_state_i_5 ^ io_state_i_6; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135243.4]
  assign _T_10 = _T_9 ^ io_state_i_7; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135244.4]
  assign _T_11 = _T_10 ^ io_state_i_8; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135245.4]
  assign bc_1 = _T_11 ^ io_state_i_9; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135246.4]
  assign _T_13 = io_state_i_10 ^ io_state_i_11; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135248.4]
  assign _T_14 = _T_13 ^ io_state_i_12; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135249.4]
  assign _T_15 = _T_14 ^ io_state_i_13; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135250.4]
  assign bc_2 = _T_15 ^ io_state_i_14; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135251.4]
  assign _T_17 = io_state_i_15 ^ io_state_i_16; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135253.4]
  assign _T_18 = _T_17 ^ io_state_i_17; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135254.4]
  assign _T_19 = _T_18 ^ io_state_i_18; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135255.4]
  assign bc_3 = _T_19 ^ io_state_i_19; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135256.4]
  assign _T_21 = io_state_i_20 ^ io_state_i_21; // @[theta.scala 21:32:example.TestHarness.Sha3RocketConfig.fir@135258.4]
  assign _T_22 = _T_21 ^ io_state_i_22; // @[theta.scala 21:52:example.TestHarness.Sha3RocketConfig.fir@135259.4]
  assign _T_23 = _T_22 ^ io_state_i_23; // @[theta.scala 21:72:example.TestHarness.Sha3RocketConfig.fir@135260.4]
  assign bc_4 = _T_23 ^ io_state_i_24; // @[theta.scala 21:92:example.TestHarness.Sha3RocketConfig.fir@135261.4]
  assign _T_26 = {bc_1, 1'h0}; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135265.4]
  assign _T_28 = 7'h40 - 7'h1; // @[common.scala 24:68:example.TestHarness.Sha3RocketConfig.fir@135267.4]
  assign _T_29 = bc_1 >> _T_28; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135268.4]
  assign _GEN_0 = {{1'd0}, _T_29}; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135269.4]
  assign _T_30 = _T_26 | _GEN_0; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135269.4]
  assign _GEN_1 = {{1'd0}, bc_4}; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135270.4]
  assign _T_31 = _GEN_1 ^ _T_30; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135270.4]
  assign _T_25 = _T_31[63:0]; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135263.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135271.4]
  assign _T_38 = {bc_2, 1'h0}; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135284.4]
  assign _T_41 = bc_2 >> _T_28; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135287.4]
  assign _GEN_2 = {{1'd0}, _T_41}; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135288.4]
  assign _T_42 = _T_38 | _GEN_2; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135288.4]
  assign _GEN_3 = {{1'd0}, bc_0}; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135289.4]
  assign _T_43 = _GEN_3 ^ _T_42; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135289.4]
  assign _T_37 = _T_43[63:0]; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135282.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135290.4]
  assign _T_50 = {bc_3, 1'h0}; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135303.4]
  assign _T_53 = bc_3 >> _T_28; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135306.4]
  assign _GEN_4 = {{1'd0}, _T_53}; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135307.4]
  assign _T_54 = _T_50 | _GEN_4; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135307.4]
  assign _GEN_5 = {{1'd0}, bc_1}; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135308.4]
  assign _T_55 = _GEN_5 ^ _T_54; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135308.4]
  assign _T_49 = _T_55[63:0]; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135301.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135309.4]
  assign _T_62 = {bc_4, 1'h0}; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135322.4]
  assign _T_65 = bc_4 >> _T_28; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135325.4]
  assign _GEN_6 = {{1'd0}, _T_65}; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135326.4]
  assign _T_66 = _T_62 | _GEN_6; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135326.4]
  assign _GEN_7 = {{1'd0}, bc_2}; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135327.4]
  assign _T_67 = _GEN_7 ^ _T_66; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135327.4]
  assign _T_61 = _T_67[63:0]; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135320.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135328.4]
  assign _T_74 = {bc_0, 1'h0}; // @[common.scala 24:47:example.TestHarness.Sha3RocketConfig.fir@135341.4]
  assign _T_77 = bc_0 >> _T_28; // @[common.scala 24:62:example.TestHarness.Sha3RocketConfig.fir@135344.4]
  assign _GEN_8 = {{1'd0}, _T_77}; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135345.4]
  assign _T_78 = _T_74 | _GEN_8; // @[common.scala 24:55:example.TestHarness.Sha3RocketConfig.fir@135345.4]
  assign _GEN_9 = {{1'd0}, bc_3}; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135346.4]
  assign _T_79 = _GEN_9 ^ _T_78; // @[theta.scala 26:22:example.TestHarness.Sha3RocketConfig.fir@135346.4]
  assign _T_73 = _T_79[63:0]; // @[theta.scala 25:17:example.TestHarness.Sha3RocketConfig.fir@135339.4 theta.scala 26:7:example.TestHarness.Sha3RocketConfig.fir@135347.4]
  assign io_state_o_0 = io_state_i_0 ^ _T_25; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135273.4]
  assign io_state_o_1 = io_state_i_1 ^ _T_25; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135275.4]
  assign io_state_o_2 = io_state_i_2 ^ _T_25; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135277.4]
  assign io_state_o_3 = io_state_i_3 ^ _T_25; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135279.4]
  assign io_state_o_4 = io_state_i_4 ^ _T_25; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135281.4]
  assign io_state_o_5 = io_state_i_5 ^ _T_37; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135292.4]
  assign io_state_o_6 = io_state_i_6 ^ _T_37; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135294.4]
  assign io_state_o_7 = io_state_i_7 ^ _T_37; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135296.4]
  assign io_state_o_8 = io_state_i_8 ^ _T_37; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135298.4]
  assign io_state_o_9 = io_state_i_9 ^ _T_37; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135300.4]
  assign io_state_o_10 = io_state_i_10 ^ _T_49; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135311.4]
  assign io_state_o_11 = io_state_i_11 ^ _T_49; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135313.4]
  assign io_state_o_12 = io_state_i_12 ^ _T_49; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135315.4]
  assign io_state_o_13 = io_state_i_13 ^ _T_49; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135317.4]
  assign io_state_o_14 = io_state_i_14 ^ _T_49; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135319.4]
  assign io_state_o_15 = io_state_i_15 ^ _T_61; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135330.4]
  assign io_state_o_16 = io_state_i_16 ^ _T_61; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135332.4]
  assign io_state_o_17 = io_state_i_17 ^ _T_61; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135334.4]
  assign io_state_o_18 = io_state_i_18 ^ _T_61; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135336.4]
  assign io_state_o_19 = io_state_i_19 ^ _T_61; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135338.4]
  assign io_state_o_20 = io_state_i_20 ^ _T_73; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135349.4]
  assign io_state_o_21 = io_state_i_21 ^ _T_73; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135351.4]
  assign io_state_o_22 = io_state_i_22 ^ _T_73; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135353.4]
  assign io_state_o_23 = io_state_i_23 ^ _T_73; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135355.4]
  assign io_state_o_24 = io_state_i_24 ^ _T_73; // @[theta.scala 28:25:example.TestHarness.Sha3RocketConfig.fir@135357.4]
endmodule
module RhoPiModule( // @[:example.TestHarness.Sha3RocketConfig.fir@135359.2]
  input  [63:0] io_state_i_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  input  [63:0] io_state_i_24, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
  output [63:0] io_state_o_24 // @[:example.TestHarness.Sha3RocketConfig.fir@135362.4]
);
  wire [28:0] _T_2; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135373.4]
  wire [35:0] _T_3; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135374.4]
  wire [64:0] _T_4; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135375.4]
  wire [61:0] _T_6; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135380.4]
  wire [2:0] _T_7; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135381.4]
  wire [64:0] _T_8; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135382.4]
  wire [23:0] _T_10; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135387.4]
  wire [40:0] _T_11; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135388.4]
  wire [64:0] _T_12; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135389.4]
  wire [46:0] _T_14; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135394.4]
  wire [17:0] _T_15; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135395.4]
  wire [64:0] _T_16; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135396.4]
  wire  _T_19; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135402.4]
  wire [64:0] _T_20; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135403.4]
  wire [20:0] _T_22; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135408.4]
  wire [43:0] _T_23; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135409.4]
  wire [64:0] _T_24; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135410.4]
  wire [54:0] _T_26; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135415.4]
  wire [9:0] _T_27; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135416.4]
  wire [64:0] _T_28; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135417.4]
  wire [19:0] _T_30; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135422.4]
  wire [44:0] _T_31; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135423.4]
  wire [64:0] _T_32; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135424.4]
  wire [62:0] _T_34; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135429.4]
  wire [1:0] _T_35; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135430.4]
  wire [64:0] _T_36; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135431.4]
  wire [2:0] _T_38; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135436.4]
  wire [61:0] _T_39; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135437.4]
  wire [64:0] _T_40; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135438.4]
  wire [58:0] _T_42; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135443.4]
  wire [5:0] _T_43; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135444.4]
  wire [64:0] _T_44; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135445.4]
  wire [21:0] _T_46; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135450.4]
  wire [42:0] _T_47; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135451.4]
  wire [64:0] _T_48; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135452.4]
  wire [49:0] _T_50; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135457.4]
  wire [14:0] _T_51; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135458.4]
  wire [64:0] _T_52; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135459.4]
  wire [3:0] _T_54; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135464.4]
  wire [60:0] _T_55; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135465.4]
  wire [64:0] _T_56; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135466.4]
  wire [36:0] _T_58; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135471.4]
  wire [27:0] _T_59; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135472.4]
  wire [64:0] _T_60; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135473.4]
  wire [9:0] _T_62; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135478.4]
  wire [54:0] _T_63; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135479.4]
  wire [64:0] _T_64; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135480.4]
  wire [39:0] _T_66; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135485.4]
  wire [24:0] _T_67; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135486.4]
  wire [64:0] _T_68; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135487.4]
  wire [43:0] _T_70; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135492.4]
  wire [20:0] _T_71; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135493.4]
  wire [64:0] _T_72; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135494.4]
  wire [8:0] _T_74; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135499.4]
  wire [55:0] _T_75; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135500.4]
  wire [64:0] _T_76; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135501.4]
  wire [37:0] _T_78; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135506.4]
  wire [26:0] _T_79; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135507.4]
  wire [64:0] _T_80; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135508.4]
  wire [44:0] _T_82; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135513.4]
  wire [19:0] _T_83; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135514.4]
  wire [64:0] _T_84; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135515.4]
  wire [25:0] _T_86; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135520.4]
  wire [38:0] _T_87; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135521.4]
  wire [64:0] _T_88; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135522.4]
  wire [56:0] _T_90; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135527.4]
  wire [7:0] _T_91; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135528.4]
  wire [64:0] _T_92; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135529.4]
  wire [50:0] _T_94; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135534.4]
  wire [13:0] _T_95; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135535.4]
  wire [64:0] _T_96; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135536.4]
  assign _T_2 = io_state_i_1[28:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135373.4]
  assign _T_3 = io_state_i_1[63:28]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135374.4]
  assign _T_4 = {_T_2,_T_3}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135375.4]
  assign _T_6 = io_state_i_2[61:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135380.4]
  assign _T_7 = io_state_i_2[63:61]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135381.4]
  assign _T_8 = {_T_6,_T_7}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135382.4]
  assign _T_10 = io_state_i_3[23:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135387.4]
  assign _T_11 = io_state_i_3[63:23]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135388.4]
  assign _T_12 = {_T_10,_T_11}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135389.4]
  assign _T_14 = io_state_i_4[46:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135394.4]
  assign _T_15 = io_state_i_4[63:46]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135395.4]
  assign _T_16 = {_T_14,_T_15}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135396.4]
  assign _T_19 = io_state_i_5[63]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135402.4]
  assign _T_20 = {io_state_i_5,_T_19}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135403.4]
  assign _T_22 = io_state_i_6[20:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135408.4]
  assign _T_23 = io_state_i_6[63:20]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135409.4]
  assign _T_24 = {_T_22,_T_23}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135410.4]
  assign _T_26 = io_state_i_7[54:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135415.4]
  assign _T_27 = io_state_i_7[63:54]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135416.4]
  assign _T_28 = {_T_26,_T_27}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135417.4]
  assign _T_30 = io_state_i_8[19:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135422.4]
  assign _T_31 = io_state_i_8[63:19]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135423.4]
  assign _T_32 = {_T_30,_T_31}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135424.4]
  assign _T_34 = io_state_i_9[62:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135429.4]
  assign _T_35 = io_state_i_9[63:62]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135430.4]
  assign _T_36 = {_T_34,_T_35}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135431.4]
  assign _T_38 = io_state_i_10[2:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135436.4]
  assign _T_39 = io_state_i_10[63:2]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135437.4]
  assign _T_40 = {_T_38,_T_39}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135438.4]
  assign _T_42 = io_state_i_11[58:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135443.4]
  assign _T_43 = io_state_i_11[63:58]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135444.4]
  assign _T_44 = {_T_42,_T_43}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135445.4]
  assign _T_46 = io_state_i_12[21:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135450.4]
  assign _T_47 = io_state_i_12[63:21]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135451.4]
  assign _T_48 = {_T_46,_T_47}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135452.4]
  assign _T_50 = io_state_i_13[49:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135457.4]
  assign _T_51 = io_state_i_13[63:49]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135458.4]
  assign _T_52 = {_T_50,_T_51}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135459.4]
  assign _T_54 = io_state_i_14[3:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135464.4]
  assign _T_55 = io_state_i_14[63:3]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135465.4]
  assign _T_56 = {_T_54,_T_55}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135466.4]
  assign _T_58 = io_state_i_15[36:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135471.4]
  assign _T_59 = io_state_i_15[63:36]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135472.4]
  assign _T_60 = {_T_58,_T_59}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135473.4]
  assign _T_62 = io_state_i_16[9:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135478.4]
  assign _T_63 = io_state_i_16[63:9]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135479.4]
  assign _T_64 = {_T_62,_T_63}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135480.4]
  assign _T_66 = io_state_i_17[39:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135485.4]
  assign _T_67 = io_state_i_17[63:39]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135486.4]
  assign _T_68 = {_T_66,_T_67}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135487.4]
  assign _T_70 = io_state_i_18[43:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135492.4]
  assign _T_71 = io_state_i_18[63:43]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135493.4]
  assign _T_72 = {_T_70,_T_71}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135494.4]
  assign _T_74 = io_state_i_19[8:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135499.4]
  assign _T_75 = io_state_i_19[63:8]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135500.4]
  assign _T_76 = {_T_74,_T_75}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135501.4]
  assign _T_78 = io_state_i_20[37:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135506.4]
  assign _T_79 = io_state_i_20[63:37]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135507.4]
  assign _T_80 = {_T_78,_T_79}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135508.4]
  assign _T_82 = io_state_i_21[44:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135513.4]
  assign _T_83 = io_state_i_21[63:44]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135514.4]
  assign _T_84 = {_T_82,_T_83}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135515.4]
  assign _T_86 = io_state_i_22[25:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135520.4]
  assign _T_87 = io_state_i_22[63:25]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135521.4]
  assign _T_88 = {_T_86,_T_87}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135522.4]
  assign _T_90 = io_state_i_23[56:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135527.4]
  assign _T_91 = io_state_i_23[63:56]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135528.4]
  assign _T_92 = {_T_90,_T_91}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135529.4]
  assign _T_94 = io_state_i_24[50:0]; // @[rhopi.scala 43:38:example.TestHarness.Sha3RocketConfig.fir@135534.4]
  assign _T_95 = io_state_i_24[63:50]; // @[rhopi.scala 43:90:example.TestHarness.Sha3RocketConfig.fir@135535.4]
  assign _T_96 = {_T_94,_T_95}; // @[Cat.scala 30:58:example.TestHarness.Sha3RocketConfig.fir@135536.4]
  assign io_state_o_0 = io_state_i_0; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135370.4]
  assign io_state_o_1 = _T_60[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135475.4]
  assign io_state_o_2 = _T_20[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135405.4]
  assign io_state_o_3 = _T_80[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135510.4]
  assign io_state_o_4 = _T_40[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135440.4]
  assign io_state_o_5 = _T_24[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135412.4]
  assign io_state_o_6 = _T_84[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135517.4]
  assign io_state_o_7 = _T_44[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135447.4]
  assign io_state_o_8 = _T_4[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135377.4]
  assign io_state_o_9 = _T_64[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135482.4]
  assign io_state_o_10 = _T_48[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135454.4]
  assign io_state_o_11 = _T_8[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135384.4]
  assign io_state_o_12 = _T_68[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135489.4]
  assign io_state_o_13 = _T_28[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135419.4]
  assign io_state_o_14 = _T_88[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135524.4]
  assign io_state_o_15 = _T_72[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135496.4]
  assign io_state_o_16 = _T_32[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135426.4]
  assign io_state_o_17 = _T_92[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135531.4]
  assign io_state_o_18 = _T_52[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135461.4]
  assign io_state_o_19 = _T_12[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135391.4]
  assign io_state_o_20 = _T_96[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135538.4]
  assign io_state_o_21 = _T_56[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135468.4]
  assign io_state_o_22 = _T_16[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135398.4]
  assign io_state_o_23 = _T_76[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135503.4]
  assign io_state_o_24 = _T_36[63:0]; // @[rhopi.scala 45:37:example.TestHarness.Sha3RocketConfig.fir@135433.4]
endmodule
module ChiModule( // @[:example.TestHarness.Sha3RocketConfig.fir@135540.2]
  input  [63:0] io_state_i_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  input  [63:0] io_state_i_24, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
  output [63:0] io_state_o_24 // @[:example.TestHarness.Sha3RocketConfig.fir@135543.4]
);
  wire [63:0] _T; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135548.4]
  wire [63:0] _T_1; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135549.4]
  wire [63:0] _T_3; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135552.4]
  wire [63:0] _T_4; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135553.4]
  wire [63:0] _T_6; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135556.4]
  wire [63:0] _T_7; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135557.4]
  wire [63:0] _T_9; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135560.4]
  wire [63:0] _T_10; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135561.4]
  wire [63:0] _T_12; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135564.4]
  wire [63:0] _T_13; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135565.4]
  wire [63:0] _T_15; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135568.4]
  wire [63:0] _T_16; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135569.4]
  wire [63:0] _T_18; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135572.4]
  wire [63:0] _T_19; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135573.4]
  wire [63:0] _T_21; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135576.4]
  wire [63:0] _T_22; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135577.4]
  wire [63:0] _T_24; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135580.4]
  wire [63:0] _T_25; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135581.4]
  wire [63:0] _T_27; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135584.4]
  wire [63:0] _T_28; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135585.4]
  wire [63:0] _T_30; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135588.4]
  wire [63:0] _T_31; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135589.4]
  wire [63:0] _T_33; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135592.4]
  wire [63:0] _T_34; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135593.4]
  wire [63:0] _T_36; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135596.4]
  wire [63:0] _T_37; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135597.4]
  wire [63:0] _T_39; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135600.4]
  wire [63:0] _T_40; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135601.4]
  wire [63:0] _T_42; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135604.4]
  wire [63:0] _T_43; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135605.4]
  wire [63:0] _T_45; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135608.4]
  wire [63:0] _T_46; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135609.4]
  wire [63:0] _T_48; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135612.4]
  wire [63:0] _T_49; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135613.4]
  wire [63:0] _T_51; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135616.4]
  wire [63:0] _T_52; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135617.4]
  wire [63:0] _T_54; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135620.4]
  wire [63:0] _T_55; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135621.4]
  wire [63:0] _T_57; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135624.4]
  wire [63:0] _T_58; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135625.4]
  wire [63:0] _T_60; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135628.4]
  wire [63:0] _T_61; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135629.4]
  wire [63:0] _T_63; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135632.4]
  wire [63:0] _T_64; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135633.4]
  wire [63:0] _T_66; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135636.4]
  wire [63:0] _T_67; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135637.4]
  wire [63:0] _T_69; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135640.4]
  wire [63:0] _T_70; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135641.4]
  wire [63:0] _T_72; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135644.4]
  wire [63:0] _T_73; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135645.4]
  assign _T = ~ io_state_i_5; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135548.4]
  assign _T_1 = _T & io_state_i_10; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135549.4]
  assign _T_3 = ~ io_state_i_6; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135552.4]
  assign _T_4 = _T_3 & io_state_i_11; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135553.4]
  assign _T_6 = ~ io_state_i_7; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135556.4]
  assign _T_7 = _T_6 & io_state_i_12; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135557.4]
  assign _T_9 = ~ io_state_i_8; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135560.4]
  assign _T_10 = _T_9 & io_state_i_13; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135561.4]
  assign _T_12 = ~ io_state_i_9; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135564.4]
  assign _T_13 = _T_12 & io_state_i_14; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135565.4]
  assign _T_15 = ~ io_state_i_10; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135568.4]
  assign _T_16 = _T_15 & io_state_i_15; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135569.4]
  assign _T_18 = ~ io_state_i_11; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135572.4]
  assign _T_19 = _T_18 & io_state_i_16; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135573.4]
  assign _T_21 = ~ io_state_i_12; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135576.4]
  assign _T_22 = _T_21 & io_state_i_17; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135577.4]
  assign _T_24 = ~ io_state_i_13; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135580.4]
  assign _T_25 = _T_24 & io_state_i_18; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135581.4]
  assign _T_27 = ~ io_state_i_14; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135584.4]
  assign _T_28 = _T_27 & io_state_i_19; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135585.4]
  assign _T_30 = ~ io_state_i_15; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135588.4]
  assign _T_31 = _T_30 & io_state_i_20; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135589.4]
  assign _T_33 = ~ io_state_i_16; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135592.4]
  assign _T_34 = _T_33 & io_state_i_21; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135593.4]
  assign _T_36 = ~ io_state_i_17; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135596.4]
  assign _T_37 = _T_36 & io_state_i_22; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135597.4]
  assign _T_39 = ~ io_state_i_18; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135600.4]
  assign _T_40 = _T_39 & io_state_i_23; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135601.4]
  assign _T_42 = ~ io_state_i_19; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135604.4]
  assign _T_43 = _T_42 & io_state_i_24; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135605.4]
  assign _T_45 = ~ io_state_i_20; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135608.4]
  assign _T_46 = _T_45 & io_state_i_0; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135609.4]
  assign _T_48 = ~ io_state_i_21; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135612.4]
  assign _T_49 = _T_48 & io_state_i_1; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135613.4]
  assign _T_51 = ~ io_state_i_22; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135616.4]
  assign _T_52 = _T_51 & io_state_i_2; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135617.4]
  assign _T_54 = ~ io_state_i_23; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135620.4]
  assign _T_55 = _T_54 & io_state_i_3; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135621.4]
  assign _T_57 = ~ io_state_i_24; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135624.4]
  assign _T_58 = _T_57 & io_state_i_4; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135625.4]
  assign _T_60 = ~ io_state_i_0; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135628.4]
  assign _T_61 = _T_60 & io_state_i_5; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135629.4]
  assign _T_63 = ~ io_state_i_1; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135632.4]
  assign _T_64 = _T_63 & io_state_i_6; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135633.4]
  assign _T_66 = ~ io_state_i_2; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135636.4]
  assign _T_67 = _T_66 & io_state_i_7; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135637.4]
  assign _T_69 = ~ io_state_i_3; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135640.4]
  assign _T_70 = _T_69 & io_state_i_8; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135641.4]
  assign _T_72 = ~ io_state_i_4; // @[chi.scala 22:10:example.TestHarness.Sha3RocketConfig.fir@135644.4]
  assign _T_73 = _T_72 & io_state_i_9; // @[chi.scala 22:44:example.TestHarness.Sha3RocketConfig.fir@135645.4]
  assign io_state_o_0 = io_state_i_0 ^ _T_1; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135551.4]
  assign io_state_o_1 = io_state_i_1 ^ _T_4; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135555.4]
  assign io_state_o_2 = io_state_i_2 ^ _T_7; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135559.4]
  assign io_state_o_3 = io_state_i_3 ^ _T_10; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135563.4]
  assign io_state_o_4 = io_state_i_4 ^ _T_13; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135567.4]
  assign io_state_o_5 = io_state_i_5 ^ _T_16; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135571.4]
  assign io_state_o_6 = io_state_i_6 ^ _T_19; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135575.4]
  assign io_state_o_7 = io_state_i_7 ^ _T_22; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135579.4]
  assign io_state_o_8 = io_state_i_8 ^ _T_25; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135583.4]
  assign io_state_o_9 = io_state_i_9 ^ _T_28; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135587.4]
  assign io_state_o_10 = io_state_i_10 ^ _T_31; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135591.4]
  assign io_state_o_11 = io_state_i_11 ^ _T_34; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135595.4]
  assign io_state_o_12 = io_state_i_12 ^ _T_37; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135599.4]
  assign io_state_o_13 = io_state_i_13 ^ _T_40; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135603.4]
  assign io_state_o_14 = io_state_i_14 ^ _T_43; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135607.4]
  assign io_state_o_15 = io_state_i_15 ^ _T_46; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135611.4]
  assign io_state_o_16 = io_state_i_16 ^ _T_49; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135615.4]
  assign io_state_o_17 = io_state_i_17 ^ _T_52; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135619.4]
  assign io_state_o_18 = io_state_i_18 ^ _T_55; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135623.4]
  assign io_state_o_19 = io_state_i_19 ^ _T_58; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135627.4]
  assign io_state_o_20 = io_state_i_20 ^ _T_61; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135631.4]
  assign io_state_o_21 = io_state_i_21 ^ _T_64; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135635.4]
  assign io_state_o_22 = io_state_i_22 ^ _T_67; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135639.4]
  assign io_state_o_23 = io_state_i_23 ^ _T_70; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135643.4]
  assign io_state_o_24 = io_state_i_24 ^ _T_73; // @[chi.scala 21:25:example.TestHarness.Sha3RocketConfig.fir@135647.4]
endmodule
module IotaModule( // @[:example.TestHarness.Sha3RocketConfig.fir@135649.2]
  input  [63:0] io_state_i_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [63:0] io_state_i_24, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_3, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_4, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_5, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_6, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_7, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_8, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_9, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_10, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_11, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_12, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_13, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_14, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_15, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_16, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_17, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_18, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_19, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_20, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_21, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_22, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_23, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  output [63:0] io_state_o_24, // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
  input  [4:0]  io_round // @[:example.TestHarness.Sha3RocketConfig.fir@135652.4]
);
  wire [63:0] _GEN_1; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_2; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_3; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_4; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_5; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_6; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_7; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_8; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_9; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_10; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_11; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_12; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_13; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_14; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_15; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_16; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_17; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_18; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_19; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_20; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_21; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_22; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_23; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  wire [63:0] _GEN_24; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_1 = 5'h1 == io_round ? 64'h8082 : 64'h1; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_2 = 5'h2 == io_round ? 64'h800000000000808a : _GEN_1; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_3 = 5'h3 == io_round ? 64'h8000000080008000 : _GEN_2; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_4 = 5'h4 == io_round ? 64'h808b : _GEN_3; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_5 = 5'h5 == io_round ? 64'h80000001 : _GEN_4; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_6 = 5'h6 == io_round ? 64'h8000000080008081 : _GEN_5; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_7 = 5'h7 == io_round ? 64'h8000000000008009 : _GEN_6; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_8 = 5'h8 == io_round ? 64'h8a : _GEN_7; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_9 = 5'h9 == io_round ? 64'h88 : _GEN_8; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_10 = 5'ha == io_round ? 64'h80008009 : _GEN_9; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_11 = 5'hb == io_round ? 64'h8000000a : _GEN_10; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_12 = 5'hc == io_round ? 64'h8000808b : _GEN_11; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_13 = 5'hd == io_round ? 64'h800000000000008b : _GEN_12; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_14 = 5'he == io_round ? 64'h8000000000008089 : _GEN_13; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_15 = 5'hf == io_round ? 64'h8000000000008003 : _GEN_14; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_16 = 5'h10 == io_round ? 64'h8000000000008002 : _GEN_15; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_17 = 5'h11 == io_round ? 64'h8000000000000080 : _GEN_16; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_18 = 5'h12 == io_round ? 64'h800a : _GEN_17; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_19 = 5'h13 == io_round ? 64'h800000008000000a : _GEN_18; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_20 = 5'h14 == io_round ? 64'h8000000080008081 : _GEN_19; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_21 = 5'h15 == io_round ? 64'h8000000000008080 : _GEN_20; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_22 = 5'h16 == io_round ? 64'h80000001 : _GEN_21; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_23 = 5'h17 == io_round ? 64'h8000000080008008 : _GEN_22; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign _GEN_24 = 5'h18 == io_round ? 64'h0 : _GEN_23; // @[iota.scala 28:34:example.TestHarness.Sha3RocketConfig.fir@135708.4]
  assign io_state_o_0 = io_state_i_0 ^ _GEN_24; // @[iota.scala 28:17:example.TestHarness.Sha3RocketConfig.fir@135709.4]
  assign io_state_o_1 = io_state_i_1; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135657.4]
  assign io_state_o_2 = io_state_i_2; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135658.4]
  assign io_state_o_3 = io_state_i_3; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135659.4]
  assign io_state_o_4 = io_state_i_4; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135660.4]
  assign io_state_o_5 = io_state_i_5; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135661.4]
  assign io_state_o_6 = io_state_i_6; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135662.4]
  assign io_state_o_7 = io_state_i_7; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135663.4]
  assign io_state_o_8 = io_state_i_8; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135664.4]
  assign io_state_o_9 = io_state_i_9; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135665.4]
  assign io_state_o_10 = io_state_i_10; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135666.4]
  assign io_state_o_11 = io_state_i_11; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135667.4]
  assign io_state_o_12 = io_state_i_12; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135668.4]
  assign io_state_o_13 = io_state_i_13; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135669.4]
  assign io_state_o_14 = io_state_i_14; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135670.4]
  assign io_state_o_15 = io_state_i_15; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135671.4]
  assign io_state_o_16 = io_state_i_16; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135672.4]
  assign io_state_o_17 = io_state_i_17; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135673.4]
  assign io_state_o_18 = io_state_i_18; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135674.4]
  assign io_state_o_19 = io_state_i_19; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135675.4]
  assign io_state_o_20 = io_state_i_20; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135676.4]
  assign io_state_o_21 = io_state_i_21; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135677.4]
  assign io_state_o_22 = io_state_i_22; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135678.4]
  assign io_state_o_23 = io_state_i_23; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135679.4]
  assign io_state_o_24 = io_state_i_24; // @[iota.scala 22:27:example.TestHarness.Sha3RocketConfig.fir@135680.4]
endmodule
module DpathModule( // @[:example.TestHarness.Sha3RocketConfig.fir@135711.2]
  input         clock, // @[:example.TestHarness.Sha3RocketConfig.fir@135712.4]
  input         reset, // @[:example.TestHarness.Sha3RocketConfig.fir@135713.4]
  input         io_absorb, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  input         io_init, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  input         io_write, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  input  [4:0]  io_round, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  input  [4:0]  io_aindex, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  input  [63:0] io_message_in, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  output [63:0] io_hash_out_0, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  output [63:0] io_hash_out_1, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  output [63:0] io_hash_out_2, // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
  output [63:0] io_hash_out_3 // @[:example.TestHarness.Sha3RocketConfig.fir@135714.4]
);
  wire [63:0] ThetaModule_io_state_i_0; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_1; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_2; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_3; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_4; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_5; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_6; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_7; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_8; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_9; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_10; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_11; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_12; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_13; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_14; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_15; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_16; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_17; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_18; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_19; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_20; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_21; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_22; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_23; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_i_24; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_0; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_1; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_2; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_3; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_4; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_5; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_6; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_7; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_8; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_9; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_10; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_11; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_12; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_13; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_14; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_15; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_16; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_17; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_18; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_19; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_20; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_21; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_22; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_23; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] ThetaModule_io_state_o_24; // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
  wire [63:0] RhoPiModule_io_state_i_0; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_1; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_2; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_3; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_4; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_5; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_6; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_7; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_8; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_9; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_10; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_11; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_12; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_13; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_14; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_15; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_16; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_17; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_18; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_19; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_20; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_21; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_22; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_23; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_i_24; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_0; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_1; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_2; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_3; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_4; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_5; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_6; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_7; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_8; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_9; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_10; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_11; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_12; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_13; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_14; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_15; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_16; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_17; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_18; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_19; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_20; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_21; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_22; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_23; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] RhoPiModule_io_state_o_24; // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
  wire [63:0] ChiModule_io_state_i_0; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_1; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_2; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_3; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_4; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_5; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_6; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_7; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_8; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_9; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_10; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_11; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_12; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_13; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_14; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_15; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_16; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_17; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_18; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_19; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_20; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_21; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_22; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_23; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_i_24; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_0; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_1; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_2; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_3; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_4; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_5; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_6; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_7; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_8; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_9; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_10; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_11; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_12; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_13; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_14; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_15; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_16; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_17; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_18; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_19; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_20; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_21; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_22; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_23; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] ChiModule_io_state_o_24; // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
  wire [63:0] iota_io_state_i_0; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_1; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_2; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_3; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_4; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_5; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_6; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_7; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_8; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_9; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_10; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_11; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_12; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_13; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_14; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_15; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_16; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_17; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_18; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_19; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_20; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_21; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_22; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_23; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_i_24; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_0; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_1; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_2; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_3; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_4; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_5; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_6; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_7; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_8; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_9; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_10; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_11; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_12; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_13; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_14; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_15; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_16; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_17; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_18; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_19; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_20; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_21; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_22; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_23; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [63:0] iota_io_state_o_24; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  wire [4:0] iota_io_round; // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
  reg [63:0] state_0; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_0;
  reg [63:0] state_1; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_1;
  reg [63:0] state_2; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_2;
  reg [63:0] state_3; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_3;
  reg [63:0] state_4; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_4;
  reg [63:0] state_5; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_5;
  reg [63:0] state_6; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_6;
  reg [63:0] state_7; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_7;
  reg [63:0] state_8; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_8;
  reg [63:0] state_9; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_9;
  reg [63:0] state_10; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_10;
  reg [63:0] state_11; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_11;
  reg [63:0] state_12; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_12;
  reg [63:0] state_13; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_13;
  reg [63:0] state_14; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_14;
  reg [63:0] state_15; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_15;
  reg [63:0] state_16; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_16;
  reg [63:0] state_17; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_17;
  reg [63:0] state_18; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_18;
  reg [63:0] state_19; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_19;
  reg [63:0] state_20; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_20;
  reg [63:0] state_21; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_21;
  reg [63:0] state_22; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_22;
  reg [63:0] state_23; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_23;
  reg [63:0] state_24; // @[dpath.scala 30:18:example.TestHarness.Sha3RocketConfig.fir@135746.4]
  reg [63:0] _RAND_24;
  wire [63:0] _GEN_0; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_1; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_2; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_3; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_4; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_5; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_6; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_7; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_8; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_9; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_10; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_11; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_12; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_13; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_14; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_15; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_16; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_17; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_18; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_19; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_20; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_21; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_22; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_23; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire [63:0] _GEN_24; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  wire  _T_6; // @[dpath.scala 104:20:example.TestHarness.Sha3RocketConfig.fir@135816.6]
  wire [4:0] _GEN_25; // @[dpath.scala 105:23:example.TestHarness.Sha3RocketConfig.fir@135818.8]
  wire [2:0] _T_7; // @[dpath.scala 105:23:example.TestHarness.Sha3RocketConfig.fir@135818.8]
  wire [5:0] _T_8; // @[dpath.scala 105:32:example.TestHarness.Sha3RocketConfig.fir@135819.8]
  wire [4:0] _T_9; // @[dpath.scala 105:51:example.TestHarness.Sha3RocketConfig.fir@135820.8]
  wire [5:0] _GEN_200; // @[dpath.scala 105:40:example.TestHarness.Sha3RocketConfig.fir@135821.8]
  wire [5:0] _T_11; // @[dpath.scala 105:40:example.TestHarness.Sha3RocketConfig.fir@135822.8]
  wire [4:0] _T_12; // @[:example.TestHarness.Sha3RocketConfig.fir@135823.8]
  wire [63:0] _GEN_26; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_27; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_28; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_29; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_30; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_31; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_32; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_33; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_34; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_35; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_36; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_37; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_38; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_39; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_40; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_41; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_42; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_43; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_44; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_45; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_46; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_47; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_48; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _GEN_49; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  wire [63:0] _T_19; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  ThetaModule ThetaModule ( // @[dpath.scala 33:21:example.TestHarness.Sha3RocketConfig.fir@135747.4]
    .io_state_i_0(ThetaModule_io_state_i_0),
    .io_state_i_1(ThetaModule_io_state_i_1),
    .io_state_i_2(ThetaModule_io_state_i_2),
    .io_state_i_3(ThetaModule_io_state_i_3),
    .io_state_i_4(ThetaModule_io_state_i_4),
    .io_state_i_5(ThetaModule_io_state_i_5),
    .io_state_i_6(ThetaModule_io_state_i_6),
    .io_state_i_7(ThetaModule_io_state_i_7),
    .io_state_i_8(ThetaModule_io_state_i_8),
    .io_state_i_9(ThetaModule_io_state_i_9),
    .io_state_i_10(ThetaModule_io_state_i_10),
    .io_state_i_11(ThetaModule_io_state_i_11),
    .io_state_i_12(ThetaModule_io_state_i_12),
    .io_state_i_13(ThetaModule_io_state_i_13),
    .io_state_i_14(ThetaModule_io_state_i_14),
    .io_state_i_15(ThetaModule_io_state_i_15),
    .io_state_i_16(ThetaModule_io_state_i_16),
    .io_state_i_17(ThetaModule_io_state_i_17),
    .io_state_i_18(ThetaModule_io_state_i_18),
    .io_state_i_19(ThetaModule_io_state_i_19),
    .io_state_i_20(ThetaModule_io_state_i_20),
    .io_state_i_21(ThetaModule_io_state_i_21),
    .io_state_i_22(ThetaModule_io_state_i_22),
    .io_state_i_23(ThetaModule_io_state_i_23),
    .io_state_i_24(ThetaModule_io_state_i_24),
    .io_state_o_0(ThetaModule_io_state_o_0),
    .io_state_o_1(ThetaModule_io_state_o_1),
    .io_state_o_2(ThetaModule_io_state_o_2),
    .io_state_o_3(ThetaModule_io_state_o_3),
    .io_state_o_4(ThetaModule_io_state_o_4),
    .io_state_o_5(ThetaModule_io_state_o_5),
    .io_state_o_6(ThetaModule_io_state_o_6),
    .io_state_o_7(ThetaModule_io_state_o_7),
    .io_state_o_8(ThetaModule_io_state_o_8),
    .io_state_o_9(ThetaModule_io_state_o_9),
    .io_state_o_10(ThetaModule_io_state_o_10),
    .io_state_o_11(ThetaModule_io_state_o_11),
    .io_state_o_12(ThetaModule_io_state_o_12),
    .io_state_o_13(ThetaModule_io_state_o_13),
    .io_state_o_14(ThetaModule_io_state_o_14),
    .io_state_o_15(ThetaModule_io_state_o_15),
    .io_state_o_16(ThetaModule_io_state_o_16),
    .io_state_o_17(ThetaModule_io_state_o_17),
    .io_state_o_18(ThetaModule_io_state_o_18),
    .io_state_o_19(ThetaModule_io_state_o_19),
    .io_state_o_20(ThetaModule_io_state_o_20),
    .io_state_o_21(ThetaModule_io_state_o_21),
    .io_state_o_22(ThetaModule_io_state_o_22),
    .io_state_o_23(ThetaModule_io_state_o_23),
    .io_state_o_24(ThetaModule_io_state_o_24)
  );
  RhoPiModule RhoPiModule ( // @[dpath.scala 34:21:example.TestHarness.Sha3RocketConfig.fir@135751.4]
    .io_state_i_0(RhoPiModule_io_state_i_0),
    .io_state_i_1(RhoPiModule_io_state_i_1),
    .io_state_i_2(RhoPiModule_io_state_i_2),
    .io_state_i_3(RhoPiModule_io_state_i_3),
    .io_state_i_4(RhoPiModule_io_state_i_4),
    .io_state_i_5(RhoPiModule_io_state_i_5),
    .io_state_i_6(RhoPiModule_io_state_i_6),
    .io_state_i_7(RhoPiModule_io_state_i_7),
    .io_state_i_8(RhoPiModule_io_state_i_8),
    .io_state_i_9(RhoPiModule_io_state_i_9),
    .io_state_i_10(RhoPiModule_io_state_i_10),
    .io_state_i_11(RhoPiModule_io_state_i_11),
    .io_state_i_12(RhoPiModule_io_state_i_12),
    .io_state_i_13(RhoPiModule_io_state_i_13),
    .io_state_i_14(RhoPiModule_io_state_i_14),
    .io_state_i_15(RhoPiModule_io_state_i_15),
    .io_state_i_16(RhoPiModule_io_state_i_16),
    .io_state_i_17(RhoPiModule_io_state_i_17),
    .io_state_i_18(RhoPiModule_io_state_i_18),
    .io_state_i_19(RhoPiModule_io_state_i_19),
    .io_state_i_20(RhoPiModule_io_state_i_20),
    .io_state_i_21(RhoPiModule_io_state_i_21),
    .io_state_i_22(RhoPiModule_io_state_i_22),
    .io_state_i_23(RhoPiModule_io_state_i_23),
    .io_state_i_24(RhoPiModule_io_state_i_24),
    .io_state_o_0(RhoPiModule_io_state_o_0),
    .io_state_o_1(RhoPiModule_io_state_o_1),
    .io_state_o_2(RhoPiModule_io_state_o_2),
    .io_state_o_3(RhoPiModule_io_state_o_3),
    .io_state_o_4(RhoPiModule_io_state_o_4),
    .io_state_o_5(RhoPiModule_io_state_o_5),
    .io_state_o_6(RhoPiModule_io_state_o_6),
    .io_state_o_7(RhoPiModule_io_state_o_7),
    .io_state_o_8(RhoPiModule_io_state_o_8),
    .io_state_o_9(RhoPiModule_io_state_o_9),
    .io_state_o_10(RhoPiModule_io_state_o_10),
    .io_state_o_11(RhoPiModule_io_state_o_11),
    .io_state_o_12(RhoPiModule_io_state_o_12),
    .io_state_o_13(RhoPiModule_io_state_o_13),
    .io_state_o_14(RhoPiModule_io_state_o_14),
    .io_state_o_15(RhoPiModule_io_state_o_15),
    .io_state_o_16(RhoPiModule_io_state_o_16),
    .io_state_o_17(RhoPiModule_io_state_o_17),
    .io_state_o_18(RhoPiModule_io_state_o_18),
    .io_state_o_19(RhoPiModule_io_state_o_19),
    .io_state_o_20(RhoPiModule_io_state_o_20),
    .io_state_o_21(RhoPiModule_io_state_o_21),
    .io_state_o_22(RhoPiModule_io_state_o_22),
    .io_state_o_23(RhoPiModule_io_state_o_23),
    .io_state_o_24(RhoPiModule_io_state_o_24)
  );
  ChiModule ChiModule ( // @[dpath.scala 35:21:example.TestHarness.Sha3RocketConfig.fir@135755.4]
    .io_state_i_0(ChiModule_io_state_i_0),
    .io_state_i_1(ChiModule_io_state_i_1),
    .io_state_i_2(ChiModule_io_state_i_2),
    .io_state_i_3(ChiModule_io_state_i_3),
    .io_state_i_4(ChiModule_io_state_i_4),
    .io_state_i_5(ChiModule_io_state_i_5),
    .io_state_i_6(ChiModule_io_state_i_6),
    .io_state_i_7(ChiModule_io_state_i_7),
    .io_state_i_8(ChiModule_io_state_i_8),
    .io_state_i_9(ChiModule_io_state_i_9),
    .io_state_i_10(ChiModule_io_state_i_10),
    .io_state_i_11(ChiModule_io_state_i_11),
    .io_state_i_12(ChiModule_io_state_i_12),
    .io_state_i_13(ChiModule_io_state_i_13),
    .io_state_i_14(ChiModule_io_state_i_14),
    .io_state_i_15(ChiModule_io_state_i_15),
    .io_state_i_16(ChiModule_io_state_i_16),
    .io_state_i_17(ChiModule_io_state_i_17),
    .io_state_i_18(ChiModule_io_state_i_18),
    .io_state_i_19(ChiModule_io_state_i_19),
    .io_state_i_20(ChiModule_io_state_i_20),
    .io_state_i_21(ChiModule_io_state_i_21),
    .io_state_i_22(ChiModule_io_state_i_22),
    .io_state_i_23(ChiModule_io_state_i_23),
    .io_state_i_24(ChiModule_io_state_i_24),
    .io_state_o_0(ChiModule_io_state_o_0),
    .io_state_o_1(ChiModule_io_state_o_1),
    .io_state_o_2(ChiModule_io_state_o_2),
    .io_state_o_3(ChiModule_io_state_o_3),
    .io_state_o_4(ChiModule_io_state_o_4),
    .io_state_o_5(ChiModule_io_state_o_5),
    .io_state_o_6(ChiModule_io_state_o_6),
    .io_state_o_7(ChiModule_io_state_o_7),
    .io_state_o_8(ChiModule_io_state_o_8),
    .io_state_o_9(ChiModule_io_state_o_9),
    .io_state_o_10(ChiModule_io_state_o_10),
    .io_state_o_11(ChiModule_io_state_o_11),
    .io_state_o_12(ChiModule_io_state_o_12),
    .io_state_o_13(ChiModule_io_state_o_13),
    .io_state_o_14(ChiModule_io_state_o_14),
    .io_state_o_15(ChiModule_io_state_o_15),
    .io_state_o_16(ChiModule_io_state_o_16),
    .io_state_o_17(ChiModule_io_state_o_17),
    .io_state_o_18(ChiModule_io_state_o_18),
    .io_state_o_19(ChiModule_io_state_o_19),
    .io_state_o_20(ChiModule_io_state_o_20),
    .io_state_o_21(ChiModule_io_state_o_21),
    .io_state_o_22(ChiModule_io_state_o_22),
    .io_state_o_23(ChiModule_io_state_o_23),
    .io_state_o_24(ChiModule_io_state_o_24)
  );
  IotaModule iota ( // @[dpath.scala 36:21:example.TestHarness.Sha3RocketConfig.fir@135759.4]
    .io_state_i_0(iota_io_state_i_0),
    .io_state_i_1(iota_io_state_i_1),
    .io_state_i_2(iota_io_state_i_2),
    .io_state_i_3(iota_io_state_i_3),
    .io_state_i_4(iota_io_state_i_4),
    .io_state_i_5(iota_io_state_i_5),
    .io_state_i_6(iota_io_state_i_6),
    .io_state_i_7(iota_io_state_i_7),
    .io_state_i_8(iota_io_state_i_8),
    .io_state_i_9(iota_io_state_i_9),
    .io_state_i_10(iota_io_state_i_10),
    .io_state_i_11(iota_io_state_i_11),
    .io_state_i_12(iota_io_state_i_12),
    .io_state_i_13(iota_io_state_i_13),
    .io_state_i_14(iota_io_state_i_14),
    .io_state_i_15(iota_io_state_i_15),
    .io_state_i_16(iota_io_state_i_16),
    .io_state_i_17(iota_io_state_i_17),
    .io_state_i_18(iota_io_state_i_18),
    .io_state_i_19(iota_io_state_i_19),
    .io_state_i_20(iota_io_state_i_20),
    .io_state_i_21(iota_io_state_i_21),
    .io_state_i_22(iota_io_state_i_22),
    .io_state_i_23(iota_io_state_i_23),
    .io_state_i_24(iota_io_state_i_24),
    .io_state_o_0(iota_io_state_o_0),
    .io_state_o_1(iota_io_state_o_1),
    .io_state_o_2(iota_io_state_o_2),
    .io_state_o_3(iota_io_state_o_3),
    .io_state_o_4(iota_io_state_o_4),
    .io_state_o_5(iota_io_state_o_5),
    .io_state_o_6(iota_io_state_o_6),
    .io_state_o_7(iota_io_state_o_7),
    .io_state_o_8(iota_io_state_o_8),
    .io_state_o_9(iota_io_state_o_9),
    .io_state_o_10(iota_io_state_o_10),
    .io_state_o_11(iota_io_state_o_11),
    .io_state_o_12(iota_io_state_o_12),
    .io_state_o_13(iota_io_state_o_13),
    .io_state_o_14(iota_io_state_o_14),
    .io_state_o_15(iota_io_state_o_15),
    .io_state_o_16(iota_io_state_o_16),
    .io_state_o_17(iota_io_state_o_17),
    .io_state_o_18(iota_io_state_o_18),
    .io_state_o_19(iota_io_state_o_19),
    .io_state_o_20(iota_io_state_o_20),
    .io_state_o_21(iota_io_state_o_21),
    .io_state_o_22(iota_io_state_o_22),
    .io_state_o_23(iota_io_state_o_23),
    .io_state_o_24(iota_io_state_o_24),
    .io_round(iota_io_round)
  );
  assign _GEN_0 = iota_io_state_o_0; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_1 = iota_io_state_o_1; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_2 = iota_io_state_o_2; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_3 = iota_io_state_o_3; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_4 = iota_io_state_o_4; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_5 = iota_io_state_o_5; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_6 = iota_io_state_o_6; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_7 = iota_io_state_o_7; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_8 = iota_io_state_o_8; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_9 = iota_io_state_o_9; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_10 = iota_io_state_o_10; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_11 = iota_io_state_o_11; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_12 = iota_io_state_o_12; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_13 = iota_io_state_o_13; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_14 = iota_io_state_o_14; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_15 = iota_io_state_o_15; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_16 = iota_io_state_o_16; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_17 = iota_io_state_o_17; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_18 = iota_io_state_o_18; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_19 = iota_io_state_o_19; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_20 = iota_io_state_o_20; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_21 = iota_io_state_o_21; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_22 = iota_io_state_o_22; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_23 = iota_io_state_o_23; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _GEN_24 = iota_io_state_o_24; // @[Conditional.scala 40:58:example.TestHarness.Sha3RocketConfig.fir@135799.4]
  assign _T_6 = io_aindex < 5'h11; // @[dpath.scala 104:20:example.TestHarness.Sha3RocketConfig.fir@135816.6]
  assign _GEN_25 = io_aindex % 5'h5; // @[dpath.scala 105:23:example.TestHarness.Sha3RocketConfig.fir@135818.8]
  assign _T_7 = _GEN_25[2:0]; // @[dpath.scala 105:23:example.TestHarness.Sha3RocketConfig.fir@135818.8]
  assign _T_8 = _T_7 * 3'h5; // @[dpath.scala 105:32:example.TestHarness.Sha3RocketConfig.fir@135819.8]
  assign _T_9 = io_aindex / 5'h5; // @[dpath.scala 105:51:example.TestHarness.Sha3RocketConfig.fir@135820.8]
  assign _GEN_200 = {{1'd0}, _T_9}; // @[dpath.scala 105:40:example.TestHarness.Sha3RocketConfig.fir@135821.8]
  assign _T_11 = _T_8 + _GEN_200; // @[dpath.scala 105:40:example.TestHarness.Sha3RocketConfig.fir@135822.8]
  assign _T_12 = _T_11[4:0]; // @[:example.TestHarness.Sha3RocketConfig.fir@135823.8]
  assign _GEN_26 = 5'h1 == _T_12 ? state_1 : state_0; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_27 = 5'h2 == _T_12 ? state_2 : _GEN_26; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_28 = 5'h3 == _T_12 ? state_3 : _GEN_27; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_29 = 5'h4 == _T_12 ? state_4 : _GEN_28; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_30 = 5'h5 == _T_12 ? state_5 : _GEN_29; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_31 = 5'h6 == _T_12 ? state_6 : _GEN_30; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_32 = 5'h7 == _T_12 ? state_7 : _GEN_31; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_33 = 5'h8 == _T_12 ? state_8 : _GEN_32; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_34 = 5'h9 == _T_12 ? state_9 : _GEN_33; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_35 = 5'ha == _T_12 ? state_10 : _GEN_34; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_36 = 5'hb == _T_12 ? state_11 : _GEN_35; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_37 = 5'hc == _T_12 ? state_12 : _GEN_36; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_38 = 5'hd == _T_12 ? state_13 : _GEN_37; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_39 = 5'he == _T_12 ? state_14 : _GEN_38; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_40 = 5'hf == _T_12 ? state_15 : _GEN_39; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_41 = 5'h10 == _T_12 ? state_16 : _GEN_40; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_42 = 5'h11 == _T_12 ? state_17 : _GEN_41; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_43 = 5'h12 == _T_12 ? state_18 : _GEN_42; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_44 = 5'h13 == _T_12 ? state_19 : _GEN_43; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_45 = 5'h14 == _T_12 ? state_20 : _GEN_44; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_46 = 5'h15 == _T_12 ? state_21 : _GEN_45; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_47 = 5'h16 == _T_12 ? state_22 : _GEN_46; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_48 = 5'h17 == _T_12 ? state_23 : _GEN_47; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _GEN_49 = 5'h18 == _T_12 ? state_24 : _GEN_48; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign _T_19 = _GEN_49 ^ io_message_in; // @[dpath.scala 106:64:example.TestHarness.Sha3RocketConfig.fir@135830.8]
  assign io_hash_out_0 = state_0; // @[dpath.scala 112:20:example.TestHarness.Sha3RocketConfig.fir@135836.4]
  assign io_hash_out_1 = state_5; // @[dpath.scala 112:20:example.TestHarness.Sha3RocketConfig.fir@135837.4]
  assign io_hash_out_2 = state_10; // @[dpath.scala 112:20:example.TestHarness.Sha3RocketConfig.fir@135838.4]
  assign io_hash_out_3 = state_15; // @[dpath.scala 112:20:example.TestHarness.Sha3RocketConfig.fir@135839.4]
  assign ThetaModule_io_state_i_0 = state_0; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_1 = state_1; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_2 = state_2; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_3 = state_3; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_4 = state_4; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_5 = state_5; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_6 = state_6; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_7 = state_7; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_8 = state_8; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_9 = state_9; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_10 = state_10; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_11 = state_11; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_12 = state_12; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_13 = state_13; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_14 = state_14; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_15 = state_15; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_16 = state_16; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_17 = state_17; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_18 = state_18; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_19 = state_19; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_20 = state_20; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_21 = state_21; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_22 = state_22; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_23 = state_23; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign ThetaModule_io_state_i_24 = state_24; // @[dpath.scala 39:17:example.TestHarness.Sha3RocketConfig.fir@135790.4 dpath.scala 44:21:example.TestHarness.Sha3RocketConfig.fir@135792.4]
  assign RhoPiModule_io_state_i_0 = ThetaModule_io_state_o_0; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_1 = ThetaModule_io_state_o_1; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_2 = ThetaModule_io_state_o_2; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_3 = ThetaModule_io_state_o_3; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_4 = ThetaModule_io_state_o_4; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_5 = ThetaModule_io_state_o_5; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_6 = ThetaModule_io_state_o_6; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_7 = ThetaModule_io_state_o_7; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_8 = ThetaModule_io_state_o_8; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_9 = ThetaModule_io_state_o_9; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_10 = ThetaModule_io_state_o_10; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_11 = ThetaModule_io_state_o_11; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_12 = ThetaModule_io_state_o_12; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_13 = ThetaModule_io_state_o_13; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_14 = ThetaModule_io_state_o_14; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_15 = ThetaModule_io_state_o_15; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_16 = ThetaModule_io_state_o_16; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_17 = ThetaModule_io_state_o_17; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_18 = ThetaModule_io_state_o_18; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_19 = ThetaModule_io_state_o_19; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_20 = ThetaModule_io_state_o_20; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_21 = ThetaModule_io_state_o_21; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_22 = ThetaModule_io_state_o_22; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_23 = ThetaModule_io_state_o_23; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign RhoPiModule_io_state_i_24 = ThetaModule_io_state_o_24; // @[dpath.scala 45:21:example.TestHarness.Sha3RocketConfig.fir@135793.4]
  assign ChiModule_io_state_i_0 = RhoPiModule_io_state_o_0; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_1 = RhoPiModule_io_state_o_1; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_2 = RhoPiModule_io_state_o_2; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_3 = RhoPiModule_io_state_o_3; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_4 = RhoPiModule_io_state_o_4; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_5 = RhoPiModule_io_state_o_5; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_6 = RhoPiModule_io_state_o_6; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_7 = RhoPiModule_io_state_o_7; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_8 = RhoPiModule_io_state_o_8; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_9 = RhoPiModule_io_state_o_9; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_10 = RhoPiModule_io_state_o_10; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_11 = RhoPiModule_io_state_o_11; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_12 = RhoPiModule_io_state_o_12; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_13 = RhoPiModule_io_state_o_13; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_14 = RhoPiModule_io_state_o_14; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_15 = RhoPiModule_io_state_o_15; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_16 = RhoPiModule_io_state_o_16; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_17 = RhoPiModule_io_state_o_17; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_18 = RhoPiModule_io_state_o_18; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_19 = RhoPiModule_io_state_o_19; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_20 = RhoPiModule_io_state_o_20; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_21 = RhoPiModule_io_state_o_21; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_22 = RhoPiModule_io_state_o_22; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_23 = RhoPiModule_io_state_o_23; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign ChiModule_io_state_i_24 = RhoPiModule_io_state_o_24; // @[dpath.scala 46:21:example.TestHarness.Sha3RocketConfig.fir@135794.4]
  assign iota_io_state_i_0 = ChiModule_io_state_o_0; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_1 = ChiModule_io_state_o_1; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_2 = ChiModule_io_state_o_2; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_3 = ChiModule_io_state_o_3; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_4 = ChiModule_io_state_o_4; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_5 = ChiModule_io_state_o_5; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_6 = ChiModule_io_state_o_6; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_7 = ChiModule_io_state_o_7; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_8 = ChiModule_io_state_o_8; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_9 = ChiModule_io_state_o_9; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_10 = ChiModule_io_state_o_10; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_11 = ChiModule_io_state_o_11; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_12 = ChiModule_io_state_o_12; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_13 = ChiModule_io_state_o_13; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_14 = ChiModule_io_state_o_14; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_15 = ChiModule_io_state_o_15; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_16 = ChiModule_io_state_o_16; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_17 = ChiModule_io_state_o_17; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_18 = ChiModule_io_state_o_18; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_19 = ChiModule_io_state_o_19; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_20 = ChiModule_io_state_o_20; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_21 = ChiModule_io_state_o_21; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_22 = ChiModule_io_state_o_22; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_23 = ChiModule_io_state_o_23; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_state_i_24 = ChiModule_io_state_o_24; // @[dpath.scala 47:24:example.TestHarness.Sha3RocketConfig.fir@135795.4]
  assign iota_io_round = io_round; // @[dpath.scala 40:21:example.TestHarness.Sha3RocketConfig.fir@135791.4 dpath.scala 70:20:example.TestHarness.Sha3RocketConfig.fir@135797.4]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {2{`RANDOM}};
  state_0 = _RAND_0[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {2{`RANDOM}};
  state_1 = _RAND_1[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {2{`RANDOM}};
  state_2 = _RAND_2[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_3 = {2{`RANDOM}};
  state_3 = _RAND_3[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_4 = {2{`RANDOM}};
  state_4 = _RAND_4[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_5 = {2{`RANDOM}};
  state_5 = _RAND_5[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_6 = {2{`RANDOM}};
  state_6 = _RAND_6[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_7 = {2{`RANDOM}};
  state_7 = _RAND_7[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_8 = {2{`RANDOM}};
  state_8 = _RAND_8[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_9 = {2{`RANDOM}};
  state_9 = _RAND_9[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_10 = {2{`RANDOM}};
  state_10 = _RAND_10[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_11 = {2{`RANDOM}};
  state_11 = _RAND_11[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_12 = {2{`RANDOM}};
  state_12 = _RAND_12[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_13 = {2{`RANDOM}};
  state_13 = _RAND_13[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_14 = {2{`RANDOM}};
  state_14 = _RAND_14[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_15 = {2{`RANDOM}};
  state_15 = _RAND_15[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_16 = {2{`RANDOM}};
  state_16 = _RAND_16[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_17 = {2{`RANDOM}};
  state_17 = _RAND_17[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_18 = {2{`RANDOM}};
  state_18 = _RAND_18[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_19 = {2{`RANDOM}};
  state_19 = _RAND_19[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_20 = {2{`RANDOM}};
  state_20 = _RAND_20[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_21 = {2{`RANDOM}};
  state_21 = _RAND_21[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_22 = {2{`RANDOM}};
  state_22 = _RAND_22[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_23 = {2{`RANDOM}};
  state_23 = _RAND_23[63:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_24 = {2{`RANDOM}};
  state_24 = _RAND_24[63:0];
  `endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end
  always @(posedge clock) begin
    if (reset) begin
      state_0 <= 64'h0;
    end else begin
      if (reset) begin
        state_0 <= 64'h0;
      end else begin
        if (io_init) begin
          state_0 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h0 == _T_12) begin
                  state_0 <= _T_19;
                end
              end
            end else begin
              state_0 <= _GEN_0;
            end
          end
        end
      end
    end
    if (reset) begin
      state_1 <= 64'h0;
    end else begin
      if (reset) begin
        state_1 <= 64'h0;
      end else begin
        if (io_init) begin
          state_1 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h1 == _T_12) begin
                  state_1 <= _T_19;
                end
              end
            end else begin
              state_1 <= _GEN_1;
            end
          end
        end
      end
    end
    if (reset) begin
      state_2 <= 64'h0;
    end else begin
      if (reset) begin
        state_2 <= 64'h0;
      end else begin
        if (io_init) begin
          state_2 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h2 == _T_12) begin
                  state_2 <= _T_19;
                end
              end
            end else begin
              state_2 <= _GEN_2;
            end
          end
        end
      end
    end
    if (reset) begin
      state_3 <= 64'h0;
    end else begin
      if (reset) begin
        state_3 <= 64'h0;
      end else begin
        if (io_init) begin
          state_3 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h3 == _T_12) begin
                  state_3 <= _T_19;
                end
              end
            end else begin
              state_3 <= _GEN_3;
            end
          end
        end
      end
    end
    if (reset) begin
      state_4 <= 64'h0;
    end else begin
      if (reset) begin
        state_4 <= 64'h0;
      end else begin
        if (io_init) begin
          state_4 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h4 == _T_12) begin
                  state_4 <= _T_19;
                end
              end
            end else begin
              state_4 <= _GEN_4;
            end
          end
        end
      end
    end
    if (reset) begin
      state_5 <= 64'h0;
    end else begin
      if (reset) begin
        state_5 <= 64'h0;
      end else begin
        if (io_init) begin
          state_5 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h5 == _T_12) begin
                  state_5 <= _T_19;
                end
              end
            end else begin
              state_5 <= _GEN_5;
            end
          end
        end
      end
    end
    if (reset) begin
      state_6 <= 64'h0;
    end else begin
      if (reset) begin
        state_6 <= 64'h0;
      end else begin
        if (io_init) begin
          state_6 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h6 == _T_12) begin
                  state_6 <= _T_19;
                end
              end
            end else begin
              state_6 <= _GEN_6;
            end
          end
        end
      end
    end
    if (reset) begin
      state_7 <= 64'h0;
    end else begin
      if (reset) begin
        state_7 <= 64'h0;
      end else begin
        if (io_init) begin
          state_7 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h7 == _T_12) begin
                  state_7 <= _T_19;
                end
              end
            end else begin
              state_7 <= _GEN_7;
            end
          end
        end
      end
    end
    if (reset) begin
      state_8 <= 64'h0;
    end else begin
      if (reset) begin
        state_8 <= 64'h0;
      end else begin
        if (io_init) begin
          state_8 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h8 == _T_12) begin
                  state_8 <= _T_19;
                end
              end
            end else begin
              state_8 <= _GEN_8;
            end
          end
        end
      end
    end
    if (reset) begin
      state_9 <= 64'h0;
    end else begin
      if (reset) begin
        state_9 <= 64'h0;
      end else begin
        if (io_init) begin
          state_9 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h9 == _T_12) begin
                  state_9 <= _T_19;
                end
              end
            end else begin
              state_9 <= _GEN_9;
            end
          end
        end
      end
    end
    if (reset) begin
      state_10 <= 64'h0;
    end else begin
      if (reset) begin
        state_10 <= 64'h0;
      end else begin
        if (io_init) begin
          state_10 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'ha == _T_12) begin
                  state_10 <= _T_19;
                end
              end
            end else begin
              state_10 <= _GEN_10;
            end
          end
        end
      end
    end
    if (reset) begin
      state_11 <= 64'h0;
    end else begin
      if (reset) begin
        state_11 <= 64'h0;
      end else begin
        if (io_init) begin
          state_11 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'hb == _T_12) begin
                  state_11 <= _T_19;
                end
              end
            end else begin
              state_11 <= _GEN_11;
            end
          end
        end
      end
    end
    if (reset) begin
      state_12 <= 64'h0;
    end else begin
      if (reset) begin
        state_12 <= 64'h0;
      end else begin
        if (io_init) begin
          state_12 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'hc == _T_12) begin
                  state_12 <= _T_19;
                end
              end
            end else begin
              state_12 <= _GEN_12;
            end
          end
        end
      end
    end
    if (reset) begin
      state_13 <= 64'h0;
    end else begin
      if (reset) begin
        state_13 <= 64'h0;
      end else begin
        if (io_init) begin
          state_13 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'hd == _T_12) begin
                  state_13 <= _T_19;
                end
              end
            end else begin
              state_13 <= _GEN_13;
            end
          end
        end
      end
    end
    if (reset) begin
      state_14 <= 64'h0;
    end else begin
      if (reset) begin
        state_14 <= 64'h0;
      end else begin
        if (io_init) begin
          state_14 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'he == _T_12) begin
                  state_14 <= _T_19;
                end
              end
            end else begin
              state_14 <= _GEN_14;
            end
          end
        end
      end
    end
    if (reset) begin
      state_15 <= 64'h0;
    end else begin
      if (reset) begin
        state_15 <= 64'h0;
      end else begin
        if (io_init) begin
          state_15 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'hf == _T_12) begin
                  state_15 <= _T_19;
                end
              end
            end else begin
              state_15 <= _GEN_15;
            end
          end
        end
      end
    end
    if (reset) begin
      state_16 <= 64'h0;
    end else begin
      if (reset) begin
        state_16 <= 64'h0;
      end else begin
        if (io_init) begin
          state_16 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h10 == _T_12) begin
                  state_16 <= _T_19;
                end
              end
            end else begin
              state_16 <= _GEN_16;
            end
          end
        end
      end
    end
    if (reset) begin
      state_17 <= 64'h0;
    end else begin
      if (reset) begin
        state_17 <= 64'h0;
      end else begin
        if (io_init) begin
          state_17 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h11 == _T_12) begin
                  state_17 <= _T_19;
                end
              end
            end else begin
              state_17 <= _GEN_17;
            end
          end
        end
      end
    end
    if (reset) begin
      state_18 <= 64'h0;
    end else begin
      if (reset) begin
        state_18 <= 64'h0;
      end else begin
        if (io_init) begin
          state_18 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h12 == _T_12) begin
                  state_18 <= _T_19;
                end
              end
            end else begin
              state_18 <= _GEN_18;
            end
          end
        end
      end
    end
    if (reset) begin
      state_19 <= 64'h0;
    end else begin
      if (reset) begin
        state_19 <= 64'h0;
      end else begin
        if (io_init) begin
          state_19 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h13 == _T_12) begin
                  state_19 <= _T_19;
                end
              end
            end else begin
              state_19 <= _GEN_19;
            end
          end
        end
      end
    end
    if (reset) begin
      state_20 <= 64'h0;
    end else begin
      if (reset) begin
        state_20 <= 64'h0;
      end else begin
        if (io_init) begin
          state_20 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h14 == _T_12) begin
                  state_20 <= _T_19;
                end
              end
            end else begin
              state_20 <= _GEN_20;
            end
          end
        end
      end
    end
    if (reset) begin
      state_21 <= 64'h0;
    end else begin
      if (reset) begin
        state_21 <= 64'h0;
      end else begin
        if (io_init) begin
          state_21 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h15 == _T_12) begin
                  state_21 <= _T_19;
                end
              end
            end else begin
              state_21 <= _GEN_21;
            end
          end
        end
      end
    end
    if (reset) begin
      state_22 <= 64'h0;
    end else begin
      if (reset) begin
        state_22 <= 64'h0;
      end else begin
        if (io_init) begin
          state_22 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h16 == _T_12) begin
                  state_22 <= _T_19;
                end
              end
            end else begin
              state_22 <= _GEN_22;
            end
          end
        end
      end
    end
    if (reset) begin
      state_23 <= 64'h0;
    end else begin
      if (reset) begin
        state_23 <= 64'h0;
      end else begin
        if (io_init) begin
          state_23 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h17 == _T_12) begin
                  state_23 <= _T_19;
                end
              end
            end else begin
              state_23 <= _GEN_23;
            end
          end
        end
      end
    end
    if (reset) begin
      state_24 <= 64'h0;
    end else begin
      if (reset) begin
        state_24 <= 64'h0;
      end else begin
        if (io_init) begin
          state_24 <= 64'h0;
        end else begin
          if (!(io_write)) begin
            if (io_absorb) begin
              if (_T_6) begin
                if (5'h18 == _T_12) begin
                  state_24 <= _T_19;
                end
              end
            end else begin
              state_24 <= _GEN_24;
            end
          end
        end
      end
    end
  end
endmodule
module Sha3BlackBox( // @[:example.TestHarness.Sha3RocketConfig.fir@135905.2]
  input         clock, // @[:example.TestHarness.Sha3RocketConfig.fir@135906.4]
  input         reset, // @[:example.TestHarness.Sha3RocketConfig.fir@135907.4]
  output        io_cmd_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [6:0]  io_cmd_bits_inst_funct, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [4:0]  io_cmd_bits_inst_rs2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [4:0]  io_cmd_bits_inst_rs1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_inst_xd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_inst_xs1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_inst_xs2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [4:0]  io_cmd_bits_inst_rd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [6:0]  io_cmd_bits_inst_opcode, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_cmd_bits_rs1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_cmd_bits_rs2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_debug, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_cease, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [31:0] io_cmd_bits_status_isa, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_dprv, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_prv, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_sd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [26:0] io_cmd_bits_status_zero2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_sxl, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_uxl, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_sd_rv32, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [7:0]  io_cmd_bits_status_zero1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_tsr, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_tw, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_tvm, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_mxr, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_sum, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_mprv, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_xs, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_fs, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_mpp, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_cmd_bits_status_hpp, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_spp, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_mpie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_hpie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_spie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_upie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_mie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_hie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_sie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_cmd_bits_status_uie, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_resp_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_resp_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [4:0]  io_resp_bits_rd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [63:0] io_resp_bits_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_req_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_req_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [39:0] io_mem_req_bits_addr, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [7:0]  io_mem_req_bits_tag, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [4:0]  io_mem_req_bits_cmd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [1:0]  io_mem_req_bits_size, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_req_bits_signed, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_req_bits_phys, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_req_bits_no_alloc, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [63:0] io_mem_req_bits_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_s1_kill, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [63:0] io_mem_s1_data_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [7:0]  io_mem_s1_data_mask, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_nack, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_nack_cause_raw, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_s2_kill, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_uncached, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [31:0] io_mem_s2_paddr, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_resp_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [39:0] io_mem_resp_bits_addr, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [7:0]  io_mem_resp_bits_tag, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [4:0]  io_mem_resp_bits_cmd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [1:0]  io_mem_resp_bits_size, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_resp_bits_signed, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_mem_resp_bits_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_resp_bits_replay, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_resp_bits_has_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_mem_resp_bits_data_word_bypass, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_mem_resp_bits_data_raw, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [63:0] io_mem_resp_bits_store_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_replay_next, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_xcpt_ma_ld, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_xcpt_ma_st, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_xcpt_pf_ld, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_xcpt_pf_st, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_xcpt_ae_ld, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_s2_xcpt_ae_st, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_ordered, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_acquire, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_release, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_grant, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_tlbMiss, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_blocked, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_canAcceptStoreThenLoad, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_canAcceptStoreThenRMW, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_canAcceptLoadThenLoad, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_storeBufferEmptyAfterLoad, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_perf_storeBufferEmptyAfterStore, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_mem_keep_clock_enabled, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_mem_clock_enabled, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_busy, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_interrupt, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_exception, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_fpu_req_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_ldst, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_wen, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_ren1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_ren2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_ren3, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_swap12, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_swap23, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_singleIn, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_singleOut, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_fromint, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_toint, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_fastpipe, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_fma, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_div, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_sqrt, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_req_bits_wflags, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [2:0]  io_fpu_req_bits_rm, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [1:0]  io_fpu_req_bits_fmaCmd, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [1:0]  io_fpu_req_bits_typ, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [64:0] io_fpu_req_bits_in1, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [64:0] io_fpu_req_bits_in2, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output [64:0] io_fpu_req_bits_in3, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  output        io_fpu_resp_ready, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input         io_fpu_resp_valid, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [64:0] io_fpu_resp_bits_data, // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
  input  [4:0]  io_fpu_resp_bits_exc // @[:example.TestHarness.Sha3RocketConfig.fir@135909.4]
);
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
  assign _T = ctrl_io_windex[1:0]; // @[:example.TestHarness.Sha3RocketConfig.fir@135941.4]
  assign _GEN_0 = dpath_io_hash_out_0; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign _GEN_1 = 2'h1 == _T ? dpath_io_hash_out_1 : _GEN_0; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign _GEN_2 = 2'h2 == _T ? dpath_io_hash_out_2 : _GEN_1; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign io_cmd_ready = ctrl_io_rocc_req_rdy; // @[sha3.scala 64:16:example.TestHarness.Sha3RocketConfig.fir@135921.4]
  assign io_resp_valid = 1'h0; // @[sha3.scala 57:17:example.TestHarness.Sha3RocketConfig.fir@135915.4]
  assign io_resp_bits_rd = 5'h0;
  assign io_resp_bits_data = 64'h0;
  assign io_mem_req_valid = ctrl_io_dmem_req_val; // @[sha3.scala 71:20:example.TestHarness.Sha3RocketConfig.fir@135927.4]
  assign io_mem_req_bits_addr = {{8'd0}, ctrl_io_dmem_req_addr}; // @[sha3.scala 74:24:example.TestHarness.Sha3RocketConfig.fir@135930.4]
  assign io_mem_req_bits_tag = {{1'd0}, ctrl_io_dmem_req_tag}; // @[sha3.scala 73:23:example.TestHarness.Sha3RocketConfig.fir@135929.4]
  assign io_mem_req_bits_cmd = ctrl_io_dmem_req_cmd; // @[sha3.scala 75:23:example.TestHarness.Sha3RocketConfig.fir@135931.4]
  assign io_mem_req_bits_size = 2'h3; // @[sha3.scala 76:24:example.TestHarness.Sha3RocketConfig.fir@135932.4]
  assign io_mem_req_bits_signed = 1'h0;
  assign io_mem_req_bits_phys = 1'h0;
  assign io_mem_req_bits_no_alloc = 1'h0;
  assign io_mem_req_bits_data = 2'h3 == _T ? dpath_io_hash_out_3 : _GEN_2; // @[sha3.scala 85:24:example.TestHarness.Sha3RocketConfig.fir@135942.4]
  assign io_mem_s1_kill = 1'h0;
  assign io_mem_s1_data_data = 64'h0;
  assign io_mem_s1_data_mask = 8'h0;
  assign io_mem_s2_kill = 1'h0;
  assign io_mem_keep_clock_enabled = 1'h0;
  assign io_busy = ctrl_io_busy; // @[sha3.scala 69:11:example.TestHarness.Sha3RocketConfig.fir@135926.4]
  assign io_interrupt = 1'h0;
  assign io_fpu_req_valid = 1'h0;
  assign io_fpu_req_bits_ldst = 1'h0;
  assign io_fpu_req_bits_wen = 1'h0;
  assign io_fpu_req_bits_ren1 = 1'h0;
  assign io_fpu_req_bits_ren2 = 1'h0;
  assign io_fpu_req_bits_ren3 = 1'h0;
  assign io_fpu_req_bits_swap12 = 1'h0;
  assign io_fpu_req_bits_swap23 = 1'h0;
  assign io_fpu_req_bits_singleIn = 1'h0;
  assign io_fpu_req_bits_singleOut = 1'h0;
  assign io_fpu_req_bits_fromint = 1'h0;
  assign io_fpu_req_bits_toint = 1'h0;
  assign io_fpu_req_bits_fastpipe = 1'h0;
  assign io_fpu_req_bits_fma = 1'h0;
  assign io_fpu_req_bits_div = 1'h0;
  assign io_fpu_req_bits_sqrt = 1'h0;
  assign io_fpu_req_bits_wflags = 1'h0;
  assign io_fpu_req_bits_rm = 3'h0;
  assign io_fpu_req_bits_fmaCmd = 2'h0;
  assign io_fpu_req_bits_typ = 2'h0;
  assign io_fpu_req_bits_in1 = 65'h0;
  assign io_fpu_req_bits_in2 = 65'h0;
  assign io_fpu_req_bits_in3 = 65'h0;
  assign io_fpu_resp_ready = 1'h0;
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
