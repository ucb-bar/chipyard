import "DPI-C" function chandle memory_init
(
  input int chip_id,
  input longint mem_size,
  input longint word_size,
  input longint line_size,
  input longint id_bits,
  input longint clock_hz,
  input longint mem_base,
  input int addr_bits
);

import "DPI-C" function void memory_tick
(
  input chandle  channel,

  input bit      reset,

  input bit      ar_valid,
  output bit     ar_ready,
  input longint  ar_addr,
  input int      ar_id,
  input int      ar_size,
  input int      ar_len,

  input bit      aw_valid,
  output bit     aw_ready,
  input longint  aw_addr,
  input int      aw_id,
  input int      aw_size,
  input int      aw_len,

  input bit      w_valid,
  output bit     w_ready,
  input int      w_strb,
  input byte     w_data[],
  input bit      w_last,

  output bit     r_valid,
  input bit      r_ready,
  output int     r_id,
  output int     r_resp,
  output byte    r_data[],
  output bit     r_last,

  output bit     b_valid,
  input bit      b_ready,
  output int     b_id,
  output int     b_resp
);

module SimDRAM #(
    parameter ADDR_BITS=32, DATA_BITS = 64, ID_BITS = 5,
              MEM_SIZE = 1000 * 1000 * 1000,
              LINE_SIZE = 64,
              WORD_SIZE = DATA_BITS/8,
              CLOCK_HZ = 100000,
              STRB_BITS = DATA_BITS/8,
              MEM_BASE = 0,
              CHIP_ID = 0)(
  input                  clock,
  input                  reset,
  output                 axi_aw_ready,
  input                  axi_aw_valid,
  input  [ADDR_BITS-1:0] axi_aw_bits_addr,
  input  [7:0]           axi_aw_bits_len,
  input  [2:0]           axi_aw_bits_size,
  input  [1:0]           axi_aw_bits_burst,
  input                  axi_aw_bits_lock,
  input  [3:0]           axi_aw_bits_cache,
  input  [2:0]           axi_aw_bits_prot,
  input  [3:0]           axi_aw_bits_qos,
  input  [ID_BITS-1:0]   axi_aw_bits_id,
  output                 axi_w_ready,
  input                  axi_w_valid,
  input  [DATA_BITS-1:0] axi_w_bits_data,
  input                  axi_w_bits_last,
  input  [STRB_BITS-1:0] axi_w_bits_strb,
  input                  axi_b_ready,
  output                 axi_b_valid,
  output [1:0]           axi_b_bits_resp,
  output [ID_BITS-1:0]   axi_b_bits_id,
  output                 axi_ar_ready,
  input                  axi_ar_valid,
  input  [ADDR_BITS-1:0] axi_ar_bits_addr,
  input  [7:0]           axi_ar_bits_len,
  input  [2:0]           axi_ar_bits_size,
  input  [1:0]           axi_ar_bits_burst,
  input                  axi_ar_bits_lock,
  input  [3:0]           axi_ar_bits_cache,
  input  [2:0]           axi_ar_bits_prot,
  input  [3:0]           axi_ar_bits_qos,
  input  [ID_BITS-1:0]   axi_ar_bits_id,
  input                  axi_r_ready,
  output                 axi_r_valid,
  output [1:0]           axi_r_bits_resp,
  output [DATA_BITS-1:0] axi_r_bits_data,
  output                 axi_r_bits_last,
  output [ID_BITS-1:0]   axi_r_bits_id
);

  reg initialized = 1'b0;
  chandle channel;

  wire __ar_valid;
  wire [63:0] __ar_addr;
  wire [31:0] __ar_id;
  wire [31:0] __ar_size;
  wire [31:0] __ar_len;

  wire __aw_valid;
  wire [63:0] __aw_addr;
  wire [31:0] __aw_id;
  wire [31:0] __aw_size;
  wire [31:0] __aw_len;

  wire __w_valid;
  wire [31:0] __w_strb;
  byte        __w_data[(DATA_BITS / 8)-1:0];
  wire        __w_last;

  wire __r_ready;
  wire __b_ready;

  bit __ar_ready;
  bit __aw_ready;
  bit __w_ready;
  bit __r_valid;
  int __r_id;
  int __r_resp;
  // bit [DATA_BITS-1:0] __r_data;
  byte __r_data[(DATA_BITS / 8)-1:0];
  bit __r_last;
  bit __b_valid;
  int __b_id;
  int __b_resp;

  reg __ar_ready_reg;
  reg __aw_ready_reg;
  reg __w_ready_reg;
  reg __r_valid_reg;
  reg [ID_BITS-1:0] __r_id_reg;
  reg [1:0] __r_resp_reg;
  reg [DATA_BITS-1:0] __r_data_bits;
  reg [DATA_BITS-1:0] __r_data_reg;
  reg __r_last_reg;
  reg __b_valid_reg;
  reg [ID_BITS-1:0] __b_id_reg;
  reg [1:0] __b_resp_reg;

  always @(posedge clock) begin
    if (reset) begin
      __ar_ready = 1'b0;
      __aw_ready = 1'b0;
      __w_ready  = 1'b0;
      __r_valid  = 1'b0;
      __b_valid  = 1'b0;

      __ar_ready_reg <= 1'b0;
      __aw_ready_reg <= 1'b0;
      __w_ready_reg  <= 1'b0;
      __r_valid_reg  <= 1'b0;
      __b_valid_reg  <= 1'b0;
    end else begin
      if (!initialized) begin
        channel = memory_init(CHIP_ID, MEM_SIZE, WORD_SIZE, LINE_SIZE, ID_BITS, CLOCK_HZ, MEM_BASE, ADDR_BITS);
        initialized = 1'b1;
      end

      memory_tick(
        channel,

        reset,

        __ar_valid,
        __ar_ready,
        __ar_addr,
        __ar_id,
        __ar_size,
        __ar_len,

        __aw_valid,
        __aw_ready,
        __aw_addr,
        __aw_id,
        __aw_size,
        __aw_len,

        __w_valid,
        __w_ready,
        __w_strb,
        __w_data,
        __w_last,

        __r_valid,
        __r_ready,
        __r_id,
        __r_resp,
        __r_data,
        __r_last,

        __b_valid,
        __b_ready,
        __b_id,
        __b_resp);

        __ar_ready_reg <= __ar_ready;
        __aw_ready_reg <= __aw_ready;
        __w_ready_reg  <= __w_ready;

        __r_valid_reg <= __r_valid;
        __r_id_reg    <= __r_id[ID_BITS-1:0];
        __r_resp_reg  <= __r_resp[1:0];
        __r_last_reg  <= __r_last;
        __r_data_bits = '0;
        for (int i = 0; i < DATA_BITS / 8; i++) begin
          __r_data_bits[i * 8 +: 8] = __r_data[i];
        end
        __r_data_reg  <= __r_data_bits;

        __b_valid_reg <= __b_valid;
        __b_id_reg    <= __b_id[ID_BITS-1:0];
        __b_resp_reg  <= __b_resp[1:0];
    end
  end

  /* verilator lint_off WIDTH */

  assign __ar_valid = axi_ar_valid;
  assign __ar_addr  = axi_ar_bits_addr;
  assign __ar_id    = axi_ar_bits_id;
  assign __ar_size  = axi_ar_bits_size;
  assign __ar_len   = axi_ar_bits_len;

  assign __aw_valid = axi_aw_valid;
  assign __aw_addr  = axi_aw_bits_addr;
  assign __aw_id    = axi_aw_bits_id;
  assign __aw_size  = axi_aw_bits_size;
  assign __aw_len   = axi_aw_bits_len;

  assign __w_valid = axi_w_valid;
  assign __w_strb  = axi_w_bits_strb;
  assign __w_last  = axi_w_bits_last;

  generate
    for (genvar i = 0; i < DATA_BITS / 8; i++) begin
      assign __w_data[i] = axi_w_bits_data[i * 8 +: 8];
    end
  endgenerate

  assign __r_ready = axi_r_ready;
  assign __b_ready = axi_b_ready;

  assign axi_ar_ready = __ar_ready_reg;
  assign axi_aw_ready = __aw_ready_reg;
  assign axi_w_ready = __w_ready_reg;
  assign axi_r_valid = __r_valid_reg;
  assign axi_r_bits_id = __r_id_reg;
  assign axi_r_bits_resp = __r_resp_reg;
  assign axi_r_bits_data = __r_data_reg;
  assign axi_r_bits_last = __r_last_reg;
  assign axi_b_valid = __b_valid_reg;
  assign axi_b_bits_id = __b_id_reg;
  assign axi_b_bits_resp = __b_resp_reg;

endmodule
