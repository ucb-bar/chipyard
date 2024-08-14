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

module AXI4AsyncCrossingSource(
  input         clock,
                reset,
                auto_in_aw_valid,
  input  [3:0]  auto_in_aw_bits_id,
  input  [31:0] auto_in_aw_bits_addr,
  input  [7:0]  auto_in_aw_bits_len,
  input  [2:0]  auto_in_aw_bits_size,
  input  [1:0]  auto_in_aw_bits_burst,
  input         auto_in_aw_bits_lock,
  input  [3:0]  auto_in_aw_bits_cache,
  input  [2:0]  auto_in_aw_bits_prot,
  input  [3:0]  auto_in_aw_bits_qos,
  input         auto_in_w_valid,
  input  [63:0] auto_in_w_bits_data,
  input  [7:0]  auto_in_w_bits_strb,
  input         auto_in_w_bits_last,
                auto_in_b_ready,
                auto_in_ar_valid,
  input  [3:0]  auto_in_ar_bits_id,
  input  [31:0] auto_in_ar_bits_addr,
  input  [7:0]  auto_in_ar_bits_len,
  input  [2:0]  auto_in_ar_bits_size,
  input  [1:0]  auto_in_ar_bits_burst,
  input         auto_in_ar_bits_lock,
  input  [3:0]  auto_in_ar_bits_cache,
  input  [2:0]  auto_in_ar_bits_prot,
  input  [3:0]  auto_in_ar_bits_qos,
  input         auto_in_r_ready,
  input  [3:0]  auto_out_aw_ridx,
  input         auto_out_aw_safe_ridx_valid,
                auto_out_aw_safe_sink_reset_n,
  input  [3:0]  auto_out_w_ridx,
  input         auto_out_w_safe_ridx_valid,
                auto_out_w_safe_sink_reset_n,
  input  [3:0]  auto_out_b_mem_0_id,
  input  [1:0]  auto_out_b_mem_0_resp,
  input  [3:0]  auto_out_b_mem_1_id,
  input  [1:0]  auto_out_b_mem_1_resp,
  input  [3:0]  auto_out_b_mem_2_id,
  input  [1:0]  auto_out_b_mem_2_resp,
  input  [3:0]  auto_out_b_mem_3_id,
  input  [1:0]  auto_out_b_mem_3_resp,
  input  [3:0]  auto_out_b_mem_4_id,
  input  [1:0]  auto_out_b_mem_4_resp,
  input  [3:0]  auto_out_b_mem_5_id,
  input  [1:0]  auto_out_b_mem_5_resp,
  input  [3:0]  auto_out_b_mem_6_id,
  input  [1:0]  auto_out_b_mem_6_resp,
  input  [3:0]  auto_out_b_mem_7_id,
  input  [1:0]  auto_out_b_mem_7_resp,
  input  [3:0]  auto_out_b_widx,
  input         auto_out_b_safe_widx_valid,
                auto_out_b_safe_source_reset_n,
  input  [3:0]  auto_out_ar_ridx,
  input         auto_out_ar_safe_ridx_valid,
                auto_out_ar_safe_sink_reset_n,
  input  [3:0]  auto_out_r_mem_0_id,
  input  [63:0] auto_out_r_mem_0_data,
  input  [1:0]  auto_out_r_mem_0_resp,
  input         auto_out_r_mem_0_last,
  input  [3:0]  auto_out_r_mem_1_id,
  input  [63:0] auto_out_r_mem_1_data,
  input  [1:0]  auto_out_r_mem_1_resp,
  input         auto_out_r_mem_1_last,
  input  [3:0]  auto_out_r_mem_2_id,
  input  [63:0] auto_out_r_mem_2_data,
  input  [1:0]  auto_out_r_mem_2_resp,
  input         auto_out_r_mem_2_last,
  input  [3:0]  auto_out_r_mem_3_id,
  input  [63:0] auto_out_r_mem_3_data,
  input  [1:0]  auto_out_r_mem_3_resp,
  input         auto_out_r_mem_3_last,
  input  [3:0]  auto_out_r_mem_4_id,
  input  [63:0] auto_out_r_mem_4_data,
  input  [1:0]  auto_out_r_mem_4_resp,
  input         auto_out_r_mem_4_last,
  input  [3:0]  auto_out_r_mem_5_id,
  input  [63:0] auto_out_r_mem_5_data,
  input  [1:0]  auto_out_r_mem_5_resp,
  input         auto_out_r_mem_5_last,
  input  [3:0]  auto_out_r_mem_6_id,
  input  [63:0] auto_out_r_mem_6_data,
  input  [1:0]  auto_out_r_mem_6_resp,
  input         auto_out_r_mem_6_last,
  input  [3:0]  auto_out_r_mem_7_id,
  input  [63:0] auto_out_r_mem_7_data,
  input  [1:0]  auto_out_r_mem_7_resp,
  input         auto_out_r_mem_7_last,
  input  [3:0]  auto_out_r_widx,
  input         auto_out_r_safe_widx_valid,
                auto_out_r_safe_source_reset_n,
  output        auto_in_aw_ready,
                auto_in_w_ready,
                auto_in_b_valid,
  output [3:0]  auto_in_b_bits_id,
  output [1:0]  auto_in_b_bits_resp,
  output        auto_in_ar_ready,
                auto_in_r_valid,
  output [3:0]  auto_in_r_bits_id,
  output [63:0] auto_in_r_bits_data,
  output [1:0]  auto_in_r_bits_resp,
  output        auto_in_r_bits_last,
  output [3:0]  auto_out_aw_mem_0_id,
  output [31:0] auto_out_aw_mem_0_addr,
  output [7:0]  auto_out_aw_mem_0_len,
  output [2:0]  auto_out_aw_mem_0_size,
  output [1:0]  auto_out_aw_mem_0_burst,
  output        auto_out_aw_mem_0_lock,
  output [3:0]  auto_out_aw_mem_0_cache,
  output [2:0]  auto_out_aw_mem_0_prot,
  output [3:0]  auto_out_aw_mem_0_qos,
                auto_out_aw_mem_1_id,
  output [31:0] auto_out_aw_mem_1_addr,
  output [7:0]  auto_out_aw_mem_1_len,
  output [2:0]  auto_out_aw_mem_1_size,
  output [1:0]  auto_out_aw_mem_1_burst,
  output        auto_out_aw_mem_1_lock,
  output [3:0]  auto_out_aw_mem_1_cache,
  output [2:0]  auto_out_aw_mem_1_prot,
  output [3:0]  auto_out_aw_mem_1_qos,
                auto_out_aw_mem_2_id,
  output [31:0] auto_out_aw_mem_2_addr,
  output [7:0]  auto_out_aw_mem_2_len,
  output [2:0]  auto_out_aw_mem_2_size,
  output [1:0]  auto_out_aw_mem_2_burst,
  output        auto_out_aw_mem_2_lock,
  output [3:0]  auto_out_aw_mem_2_cache,
  output [2:0]  auto_out_aw_mem_2_prot,
  output [3:0]  auto_out_aw_mem_2_qos,
                auto_out_aw_mem_3_id,
  output [31:0] auto_out_aw_mem_3_addr,
  output [7:0]  auto_out_aw_mem_3_len,
  output [2:0]  auto_out_aw_mem_3_size,
  output [1:0]  auto_out_aw_mem_3_burst,
  output        auto_out_aw_mem_3_lock,
  output [3:0]  auto_out_aw_mem_3_cache,
  output [2:0]  auto_out_aw_mem_3_prot,
  output [3:0]  auto_out_aw_mem_3_qos,
                auto_out_aw_mem_4_id,
  output [31:0] auto_out_aw_mem_4_addr,
  output [7:0]  auto_out_aw_mem_4_len,
  output [2:0]  auto_out_aw_mem_4_size,
  output [1:0]  auto_out_aw_mem_4_burst,
  output        auto_out_aw_mem_4_lock,
  output [3:0]  auto_out_aw_mem_4_cache,
  output [2:0]  auto_out_aw_mem_4_prot,
  output [3:0]  auto_out_aw_mem_4_qos,
                auto_out_aw_mem_5_id,
  output [31:0] auto_out_aw_mem_5_addr,
  output [7:0]  auto_out_aw_mem_5_len,
  output [2:0]  auto_out_aw_mem_5_size,
  output [1:0]  auto_out_aw_mem_5_burst,
  output        auto_out_aw_mem_5_lock,
  output [3:0]  auto_out_aw_mem_5_cache,
  output [2:0]  auto_out_aw_mem_5_prot,
  output [3:0]  auto_out_aw_mem_5_qos,
                auto_out_aw_mem_6_id,
  output [31:0] auto_out_aw_mem_6_addr,
  output [7:0]  auto_out_aw_mem_6_len,
  output [2:0]  auto_out_aw_mem_6_size,
  output [1:0]  auto_out_aw_mem_6_burst,
  output        auto_out_aw_mem_6_lock,
  output [3:0]  auto_out_aw_mem_6_cache,
  output [2:0]  auto_out_aw_mem_6_prot,
  output [3:0]  auto_out_aw_mem_6_qos,
                auto_out_aw_mem_7_id,
  output [31:0] auto_out_aw_mem_7_addr,
  output [7:0]  auto_out_aw_mem_7_len,
  output [2:0]  auto_out_aw_mem_7_size,
  output [1:0]  auto_out_aw_mem_7_burst,
  output        auto_out_aw_mem_7_lock,
  output [3:0]  auto_out_aw_mem_7_cache,
  output [2:0]  auto_out_aw_mem_7_prot,
  output [3:0]  auto_out_aw_mem_7_qos,
                auto_out_aw_widx,
  output        auto_out_aw_safe_widx_valid,
                auto_out_aw_safe_source_reset_n,
  output [63:0] auto_out_w_mem_0_data,
  output [7:0]  auto_out_w_mem_0_strb,
  output        auto_out_w_mem_0_last,
  output [63:0] auto_out_w_mem_1_data,
  output [7:0]  auto_out_w_mem_1_strb,
  output        auto_out_w_mem_1_last,
  output [63:0] auto_out_w_mem_2_data,
  output [7:0]  auto_out_w_mem_2_strb,
  output        auto_out_w_mem_2_last,
  output [63:0] auto_out_w_mem_3_data,
  output [7:0]  auto_out_w_mem_3_strb,
  output        auto_out_w_mem_3_last,
  output [63:0] auto_out_w_mem_4_data,
  output [7:0]  auto_out_w_mem_4_strb,
  output        auto_out_w_mem_4_last,
  output [63:0] auto_out_w_mem_5_data,
  output [7:0]  auto_out_w_mem_5_strb,
  output        auto_out_w_mem_5_last,
  output [63:0] auto_out_w_mem_6_data,
  output [7:0]  auto_out_w_mem_6_strb,
  output        auto_out_w_mem_6_last,
  output [63:0] auto_out_w_mem_7_data,
  output [7:0]  auto_out_w_mem_7_strb,
  output        auto_out_w_mem_7_last,
  output [3:0]  auto_out_w_widx,
  output        auto_out_w_safe_widx_valid,
                auto_out_w_safe_source_reset_n,
  output [3:0]  auto_out_b_ridx,
  output        auto_out_b_safe_ridx_valid,
                auto_out_b_safe_sink_reset_n,
  output [3:0]  auto_out_ar_mem_0_id,
  output [31:0] auto_out_ar_mem_0_addr,
  output [7:0]  auto_out_ar_mem_0_len,
  output [2:0]  auto_out_ar_mem_0_size,
  output [1:0]  auto_out_ar_mem_0_burst,
  output        auto_out_ar_mem_0_lock,
  output [3:0]  auto_out_ar_mem_0_cache,
  output [2:0]  auto_out_ar_mem_0_prot,
  output [3:0]  auto_out_ar_mem_0_qos,
                auto_out_ar_mem_1_id,
  output [31:0] auto_out_ar_mem_1_addr,
  output [7:0]  auto_out_ar_mem_1_len,
  output [2:0]  auto_out_ar_mem_1_size,
  output [1:0]  auto_out_ar_mem_1_burst,
  output        auto_out_ar_mem_1_lock,
  output [3:0]  auto_out_ar_mem_1_cache,
  output [2:0]  auto_out_ar_mem_1_prot,
  output [3:0]  auto_out_ar_mem_1_qos,
                auto_out_ar_mem_2_id,
  output [31:0] auto_out_ar_mem_2_addr,
  output [7:0]  auto_out_ar_mem_2_len,
  output [2:0]  auto_out_ar_mem_2_size,
  output [1:0]  auto_out_ar_mem_2_burst,
  output        auto_out_ar_mem_2_lock,
  output [3:0]  auto_out_ar_mem_2_cache,
  output [2:0]  auto_out_ar_mem_2_prot,
  output [3:0]  auto_out_ar_mem_2_qos,
                auto_out_ar_mem_3_id,
  output [31:0] auto_out_ar_mem_3_addr,
  output [7:0]  auto_out_ar_mem_3_len,
  output [2:0]  auto_out_ar_mem_3_size,
  output [1:0]  auto_out_ar_mem_3_burst,
  output        auto_out_ar_mem_3_lock,
  output [3:0]  auto_out_ar_mem_3_cache,
  output [2:0]  auto_out_ar_mem_3_prot,
  output [3:0]  auto_out_ar_mem_3_qos,
                auto_out_ar_mem_4_id,
  output [31:0] auto_out_ar_mem_4_addr,
  output [7:0]  auto_out_ar_mem_4_len,
  output [2:0]  auto_out_ar_mem_4_size,
  output [1:0]  auto_out_ar_mem_4_burst,
  output        auto_out_ar_mem_4_lock,
  output [3:0]  auto_out_ar_mem_4_cache,
  output [2:0]  auto_out_ar_mem_4_prot,
  output [3:0]  auto_out_ar_mem_4_qos,
                auto_out_ar_mem_5_id,
  output [31:0] auto_out_ar_mem_5_addr,
  output [7:0]  auto_out_ar_mem_5_len,
  output [2:0]  auto_out_ar_mem_5_size,
  output [1:0]  auto_out_ar_mem_5_burst,
  output        auto_out_ar_mem_5_lock,
  output [3:0]  auto_out_ar_mem_5_cache,
  output [2:0]  auto_out_ar_mem_5_prot,
  output [3:0]  auto_out_ar_mem_5_qos,
                auto_out_ar_mem_6_id,
  output [31:0] auto_out_ar_mem_6_addr,
  output [7:0]  auto_out_ar_mem_6_len,
  output [2:0]  auto_out_ar_mem_6_size,
  output [1:0]  auto_out_ar_mem_6_burst,
  output        auto_out_ar_mem_6_lock,
  output [3:0]  auto_out_ar_mem_6_cache,
  output [2:0]  auto_out_ar_mem_6_prot,
  output [3:0]  auto_out_ar_mem_6_qos,
                auto_out_ar_mem_7_id,
  output [31:0] auto_out_ar_mem_7_addr,
  output [7:0]  auto_out_ar_mem_7_len,
  output [2:0]  auto_out_ar_mem_7_size,
  output [1:0]  auto_out_ar_mem_7_burst,
  output        auto_out_ar_mem_7_lock,
  output [3:0]  auto_out_ar_mem_7_cache,
  output [2:0]  auto_out_ar_mem_7_prot,
  output [3:0]  auto_out_ar_mem_7_qos,
                auto_out_ar_widx,
  output        auto_out_ar_safe_widx_valid,
                auto_out_ar_safe_source_reset_n,
  output [3:0]  auto_out_r_ridx,
  output        auto_out_r_safe_ridx_valid,
                auto_out_r_safe_sink_reset_n
);

  AsyncQueueSource_2 x1_ar_source (	// @[AsyncQueue.scala:216:24]
    .clock                        (clock),
    .reset                        (reset),
    .io_enq_valid                 (auto_in_ar_valid),
    .io_enq_bits_id               (auto_in_ar_bits_id),
    .io_enq_bits_addr             (auto_in_ar_bits_addr),
    .io_enq_bits_len              (auto_in_ar_bits_len),
    .io_enq_bits_size             (auto_in_ar_bits_size),
    .io_enq_bits_burst            (auto_in_ar_bits_burst),
    .io_enq_bits_lock             (auto_in_ar_bits_lock),
    .io_enq_bits_cache            (auto_in_ar_bits_cache),
    .io_enq_bits_prot             (auto_in_ar_bits_prot),
    .io_enq_bits_qos              (auto_in_ar_bits_qos),
    .io_async_ridx                (auto_out_ar_ridx),
    .io_async_safe_ridx_valid     (auto_out_ar_safe_ridx_valid),
    .io_async_safe_sink_reset_n   (auto_out_ar_safe_sink_reset_n),
    .io_enq_ready                 (auto_in_ar_ready),
    .io_async_mem_0_id            (auto_out_ar_mem_0_id),
    .io_async_mem_0_addr          (auto_out_ar_mem_0_addr),
    .io_async_mem_0_len           (auto_out_ar_mem_0_len),
    .io_async_mem_0_size          (auto_out_ar_mem_0_size),
    .io_async_mem_0_burst         (auto_out_ar_mem_0_burst),
    .io_async_mem_0_lock          (auto_out_ar_mem_0_lock),
    .io_async_mem_0_cache         (auto_out_ar_mem_0_cache),
    .io_async_mem_0_prot          (auto_out_ar_mem_0_prot),
    .io_async_mem_0_qos           (auto_out_ar_mem_0_qos),
    .io_async_mem_1_id            (auto_out_ar_mem_1_id),
    .io_async_mem_1_addr          (auto_out_ar_mem_1_addr),
    .io_async_mem_1_len           (auto_out_ar_mem_1_len),
    .io_async_mem_1_size          (auto_out_ar_mem_1_size),
    .io_async_mem_1_burst         (auto_out_ar_mem_1_burst),
    .io_async_mem_1_lock          (auto_out_ar_mem_1_lock),
    .io_async_mem_1_cache         (auto_out_ar_mem_1_cache),
    .io_async_mem_1_prot          (auto_out_ar_mem_1_prot),
    .io_async_mem_1_qos           (auto_out_ar_mem_1_qos),
    .io_async_mem_2_id            (auto_out_ar_mem_2_id),
    .io_async_mem_2_addr          (auto_out_ar_mem_2_addr),
    .io_async_mem_2_len           (auto_out_ar_mem_2_len),
    .io_async_mem_2_size          (auto_out_ar_mem_2_size),
    .io_async_mem_2_burst         (auto_out_ar_mem_2_burst),
    .io_async_mem_2_lock          (auto_out_ar_mem_2_lock),
    .io_async_mem_2_cache         (auto_out_ar_mem_2_cache),
    .io_async_mem_2_prot          (auto_out_ar_mem_2_prot),
    .io_async_mem_2_qos           (auto_out_ar_mem_2_qos),
    .io_async_mem_3_id            (auto_out_ar_mem_3_id),
    .io_async_mem_3_addr          (auto_out_ar_mem_3_addr),
    .io_async_mem_3_len           (auto_out_ar_mem_3_len),
    .io_async_mem_3_size          (auto_out_ar_mem_3_size),
    .io_async_mem_3_burst         (auto_out_ar_mem_3_burst),
    .io_async_mem_3_lock          (auto_out_ar_mem_3_lock),
    .io_async_mem_3_cache         (auto_out_ar_mem_3_cache),
    .io_async_mem_3_prot          (auto_out_ar_mem_3_prot),
    .io_async_mem_3_qos           (auto_out_ar_mem_3_qos),
    .io_async_mem_4_id            (auto_out_ar_mem_4_id),
    .io_async_mem_4_addr          (auto_out_ar_mem_4_addr),
    .io_async_mem_4_len           (auto_out_ar_mem_4_len),
    .io_async_mem_4_size          (auto_out_ar_mem_4_size),
    .io_async_mem_4_burst         (auto_out_ar_mem_4_burst),
    .io_async_mem_4_lock          (auto_out_ar_mem_4_lock),
    .io_async_mem_4_cache         (auto_out_ar_mem_4_cache),
    .io_async_mem_4_prot          (auto_out_ar_mem_4_prot),
    .io_async_mem_4_qos           (auto_out_ar_mem_4_qos),
    .io_async_mem_5_id            (auto_out_ar_mem_5_id),
    .io_async_mem_5_addr          (auto_out_ar_mem_5_addr),
    .io_async_mem_5_len           (auto_out_ar_mem_5_len),
    .io_async_mem_5_size          (auto_out_ar_mem_5_size),
    .io_async_mem_5_burst         (auto_out_ar_mem_5_burst),
    .io_async_mem_5_lock          (auto_out_ar_mem_5_lock),
    .io_async_mem_5_cache         (auto_out_ar_mem_5_cache),
    .io_async_mem_5_prot          (auto_out_ar_mem_5_prot),
    .io_async_mem_5_qos           (auto_out_ar_mem_5_qos),
    .io_async_mem_6_id            (auto_out_ar_mem_6_id),
    .io_async_mem_6_addr          (auto_out_ar_mem_6_addr),
    .io_async_mem_6_len           (auto_out_ar_mem_6_len),
    .io_async_mem_6_size          (auto_out_ar_mem_6_size),
    .io_async_mem_6_burst         (auto_out_ar_mem_6_burst),
    .io_async_mem_6_lock          (auto_out_ar_mem_6_lock),
    .io_async_mem_6_cache         (auto_out_ar_mem_6_cache),
    .io_async_mem_6_prot          (auto_out_ar_mem_6_prot),
    .io_async_mem_6_qos           (auto_out_ar_mem_6_qos),
    .io_async_mem_7_id            (auto_out_ar_mem_7_id),
    .io_async_mem_7_addr          (auto_out_ar_mem_7_addr),
    .io_async_mem_7_len           (auto_out_ar_mem_7_len),
    .io_async_mem_7_size          (auto_out_ar_mem_7_size),
    .io_async_mem_7_burst         (auto_out_ar_mem_7_burst),
    .io_async_mem_7_lock          (auto_out_ar_mem_7_lock),
    .io_async_mem_7_cache         (auto_out_ar_mem_7_cache),
    .io_async_mem_7_prot          (auto_out_ar_mem_7_prot),
    .io_async_mem_7_qos           (auto_out_ar_mem_7_qos),
    .io_async_widx                (auto_out_ar_widx),
    .io_async_safe_widx_valid     (auto_out_ar_safe_widx_valid),
    .io_async_safe_source_reset_n (auto_out_ar_safe_source_reset_n)
  );
  AsyncQueueSource_2 x1_aw_source (	// @[AsyncQueue.scala:216:24]
    .clock                        (clock),
    .reset                        (reset),
    .io_enq_valid                 (auto_in_aw_valid),
    .io_enq_bits_id               (auto_in_aw_bits_id),
    .io_enq_bits_addr             (auto_in_aw_bits_addr),
    .io_enq_bits_len              (auto_in_aw_bits_len),
    .io_enq_bits_size             (auto_in_aw_bits_size),
    .io_enq_bits_burst            (auto_in_aw_bits_burst),
    .io_enq_bits_lock             (auto_in_aw_bits_lock),
    .io_enq_bits_cache            (auto_in_aw_bits_cache),
    .io_enq_bits_prot             (auto_in_aw_bits_prot),
    .io_enq_bits_qos              (auto_in_aw_bits_qos),
    .io_async_ridx                (auto_out_aw_ridx),
    .io_async_safe_ridx_valid     (auto_out_aw_safe_ridx_valid),
    .io_async_safe_sink_reset_n   (auto_out_aw_safe_sink_reset_n),
    .io_enq_ready                 (auto_in_aw_ready),
    .io_async_mem_0_id            (auto_out_aw_mem_0_id),
    .io_async_mem_0_addr          (auto_out_aw_mem_0_addr),
    .io_async_mem_0_len           (auto_out_aw_mem_0_len),
    .io_async_mem_0_size          (auto_out_aw_mem_0_size),
    .io_async_mem_0_burst         (auto_out_aw_mem_0_burst),
    .io_async_mem_0_lock          (auto_out_aw_mem_0_lock),
    .io_async_mem_0_cache         (auto_out_aw_mem_0_cache),
    .io_async_mem_0_prot          (auto_out_aw_mem_0_prot),
    .io_async_mem_0_qos           (auto_out_aw_mem_0_qos),
    .io_async_mem_1_id            (auto_out_aw_mem_1_id),
    .io_async_mem_1_addr          (auto_out_aw_mem_1_addr),
    .io_async_mem_1_len           (auto_out_aw_mem_1_len),
    .io_async_mem_1_size          (auto_out_aw_mem_1_size),
    .io_async_mem_1_burst         (auto_out_aw_mem_1_burst),
    .io_async_mem_1_lock          (auto_out_aw_mem_1_lock),
    .io_async_mem_1_cache         (auto_out_aw_mem_1_cache),
    .io_async_mem_1_prot          (auto_out_aw_mem_1_prot),
    .io_async_mem_1_qos           (auto_out_aw_mem_1_qos),
    .io_async_mem_2_id            (auto_out_aw_mem_2_id),
    .io_async_mem_2_addr          (auto_out_aw_mem_2_addr),
    .io_async_mem_2_len           (auto_out_aw_mem_2_len),
    .io_async_mem_2_size          (auto_out_aw_mem_2_size),
    .io_async_mem_2_burst         (auto_out_aw_mem_2_burst),
    .io_async_mem_2_lock          (auto_out_aw_mem_2_lock),
    .io_async_mem_2_cache         (auto_out_aw_mem_2_cache),
    .io_async_mem_2_prot          (auto_out_aw_mem_2_prot),
    .io_async_mem_2_qos           (auto_out_aw_mem_2_qos),
    .io_async_mem_3_id            (auto_out_aw_mem_3_id),
    .io_async_mem_3_addr          (auto_out_aw_mem_3_addr),
    .io_async_mem_3_len           (auto_out_aw_mem_3_len),
    .io_async_mem_3_size          (auto_out_aw_mem_3_size),
    .io_async_mem_3_burst         (auto_out_aw_mem_3_burst),
    .io_async_mem_3_lock          (auto_out_aw_mem_3_lock),
    .io_async_mem_3_cache         (auto_out_aw_mem_3_cache),
    .io_async_mem_3_prot          (auto_out_aw_mem_3_prot),
    .io_async_mem_3_qos           (auto_out_aw_mem_3_qos),
    .io_async_mem_4_id            (auto_out_aw_mem_4_id),
    .io_async_mem_4_addr          (auto_out_aw_mem_4_addr),
    .io_async_mem_4_len           (auto_out_aw_mem_4_len),
    .io_async_mem_4_size          (auto_out_aw_mem_4_size),
    .io_async_mem_4_burst         (auto_out_aw_mem_4_burst),
    .io_async_mem_4_lock          (auto_out_aw_mem_4_lock),
    .io_async_mem_4_cache         (auto_out_aw_mem_4_cache),
    .io_async_mem_4_prot          (auto_out_aw_mem_4_prot),
    .io_async_mem_4_qos           (auto_out_aw_mem_4_qos),
    .io_async_mem_5_id            (auto_out_aw_mem_5_id),
    .io_async_mem_5_addr          (auto_out_aw_mem_5_addr),
    .io_async_mem_5_len           (auto_out_aw_mem_5_len),
    .io_async_mem_5_size          (auto_out_aw_mem_5_size),
    .io_async_mem_5_burst         (auto_out_aw_mem_5_burst),
    .io_async_mem_5_lock          (auto_out_aw_mem_5_lock),
    .io_async_mem_5_cache         (auto_out_aw_mem_5_cache),
    .io_async_mem_5_prot          (auto_out_aw_mem_5_prot),
    .io_async_mem_5_qos           (auto_out_aw_mem_5_qos),
    .io_async_mem_6_id            (auto_out_aw_mem_6_id),
    .io_async_mem_6_addr          (auto_out_aw_mem_6_addr),
    .io_async_mem_6_len           (auto_out_aw_mem_6_len),
    .io_async_mem_6_size          (auto_out_aw_mem_6_size),
    .io_async_mem_6_burst         (auto_out_aw_mem_6_burst),
    .io_async_mem_6_lock          (auto_out_aw_mem_6_lock),
    .io_async_mem_6_cache         (auto_out_aw_mem_6_cache),
    .io_async_mem_6_prot          (auto_out_aw_mem_6_prot),
    .io_async_mem_6_qos           (auto_out_aw_mem_6_qos),
    .io_async_mem_7_id            (auto_out_aw_mem_7_id),
    .io_async_mem_7_addr          (auto_out_aw_mem_7_addr),
    .io_async_mem_7_len           (auto_out_aw_mem_7_len),
    .io_async_mem_7_size          (auto_out_aw_mem_7_size),
    .io_async_mem_7_burst         (auto_out_aw_mem_7_burst),
    .io_async_mem_7_lock          (auto_out_aw_mem_7_lock),
    .io_async_mem_7_cache         (auto_out_aw_mem_7_cache),
    .io_async_mem_7_prot          (auto_out_aw_mem_7_prot),
    .io_async_mem_7_qos           (auto_out_aw_mem_7_qos),
    .io_async_widx                (auto_out_aw_widx),
    .io_async_safe_widx_valid     (auto_out_aw_safe_widx_valid),
    .io_async_safe_source_reset_n (auto_out_aw_safe_source_reset_n)
  );
  AsyncQueueSource_4 x1_w_source (	// @[AsyncQueue.scala:216:24]
    .clock                        (clock),
    .reset                        (reset),
    .io_enq_valid                 (auto_in_w_valid),
    .io_enq_bits_data             (auto_in_w_bits_data),
    .io_enq_bits_strb             (auto_in_w_bits_strb),
    .io_enq_bits_last             (auto_in_w_bits_last),
    .io_async_ridx                (auto_out_w_ridx),
    .io_async_safe_ridx_valid     (auto_out_w_safe_ridx_valid),
    .io_async_safe_sink_reset_n   (auto_out_w_safe_sink_reset_n),
    .io_enq_ready                 (auto_in_w_ready),
    .io_async_mem_0_data          (auto_out_w_mem_0_data),
    .io_async_mem_0_strb          (auto_out_w_mem_0_strb),
    .io_async_mem_0_last          (auto_out_w_mem_0_last),
    .io_async_mem_1_data          (auto_out_w_mem_1_data),
    .io_async_mem_1_strb          (auto_out_w_mem_1_strb),
    .io_async_mem_1_last          (auto_out_w_mem_1_last),
    .io_async_mem_2_data          (auto_out_w_mem_2_data),
    .io_async_mem_2_strb          (auto_out_w_mem_2_strb),
    .io_async_mem_2_last          (auto_out_w_mem_2_last),
    .io_async_mem_3_data          (auto_out_w_mem_3_data),
    .io_async_mem_3_strb          (auto_out_w_mem_3_strb),
    .io_async_mem_3_last          (auto_out_w_mem_3_last),
    .io_async_mem_4_data          (auto_out_w_mem_4_data),
    .io_async_mem_4_strb          (auto_out_w_mem_4_strb),
    .io_async_mem_4_last          (auto_out_w_mem_4_last),
    .io_async_mem_5_data          (auto_out_w_mem_5_data),
    .io_async_mem_5_strb          (auto_out_w_mem_5_strb),
    .io_async_mem_5_last          (auto_out_w_mem_5_last),
    .io_async_mem_6_data          (auto_out_w_mem_6_data),
    .io_async_mem_6_strb          (auto_out_w_mem_6_strb),
    .io_async_mem_6_last          (auto_out_w_mem_6_last),
    .io_async_mem_7_data          (auto_out_w_mem_7_data),
    .io_async_mem_7_strb          (auto_out_w_mem_7_strb),
    .io_async_mem_7_last          (auto_out_w_mem_7_last),
    .io_async_widx                (auto_out_w_widx),
    .io_async_safe_widx_valid     (auto_out_w_safe_widx_valid),
    .io_async_safe_source_reset_n (auto_out_w_safe_source_reset_n)
  );
  AsyncQueueSink_3 bundleIn_0_r_sink (	// @[AsyncQueue.scala:207:22]
    .clock                        (clock),
    .reset                        (reset),
    .io_deq_ready                 (auto_in_r_ready),
    .io_async_mem_0_id            (auto_out_r_mem_0_id),
    .io_async_mem_0_data          (auto_out_r_mem_0_data),
    .io_async_mem_0_resp          (auto_out_r_mem_0_resp),
    .io_async_mem_0_last          (auto_out_r_mem_0_last),
    .io_async_mem_1_id            (auto_out_r_mem_1_id),
    .io_async_mem_1_data          (auto_out_r_mem_1_data),
    .io_async_mem_1_resp          (auto_out_r_mem_1_resp),
    .io_async_mem_1_last          (auto_out_r_mem_1_last),
    .io_async_mem_2_id            (auto_out_r_mem_2_id),
    .io_async_mem_2_data          (auto_out_r_mem_2_data),
    .io_async_mem_2_resp          (auto_out_r_mem_2_resp),
    .io_async_mem_2_last          (auto_out_r_mem_2_last),
    .io_async_mem_3_id            (auto_out_r_mem_3_id),
    .io_async_mem_3_data          (auto_out_r_mem_3_data),
    .io_async_mem_3_resp          (auto_out_r_mem_3_resp),
    .io_async_mem_3_last          (auto_out_r_mem_3_last),
    .io_async_mem_4_id            (auto_out_r_mem_4_id),
    .io_async_mem_4_data          (auto_out_r_mem_4_data),
    .io_async_mem_4_resp          (auto_out_r_mem_4_resp),
    .io_async_mem_4_last          (auto_out_r_mem_4_last),
    .io_async_mem_5_id            (auto_out_r_mem_5_id),
    .io_async_mem_5_data          (auto_out_r_mem_5_data),
    .io_async_mem_5_resp          (auto_out_r_mem_5_resp),
    .io_async_mem_5_last          (auto_out_r_mem_5_last),
    .io_async_mem_6_id            (auto_out_r_mem_6_id),
    .io_async_mem_6_data          (auto_out_r_mem_6_data),
    .io_async_mem_6_resp          (auto_out_r_mem_6_resp),
    .io_async_mem_6_last          (auto_out_r_mem_6_last),
    .io_async_mem_7_id            (auto_out_r_mem_7_id),
    .io_async_mem_7_data          (auto_out_r_mem_7_data),
    .io_async_mem_7_resp          (auto_out_r_mem_7_resp),
    .io_async_mem_7_last          (auto_out_r_mem_7_last),
    .io_async_widx                (auto_out_r_widx),
    .io_async_safe_widx_valid     (auto_out_r_safe_widx_valid),
    .io_async_safe_source_reset_n (auto_out_r_safe_source_reset_n),
    .io_deq_valid                 (auto_in_r_valid),
    .io_deq_bits_id               (auto_in_r_bits_id),
    .io_deq_bits_data             (auto_in_r_bits_data),
    .io_deq_bits_resp             (auto_in_r_bits_resp),
    .io_deq_bits_last             (auto_in_r_bits_last),
    .io_async_ridx                (auto_out_r_ridx),
    .io_async_safe_ridx_valid     (auto_out_r_safe_ridx_valid),
    .io_async_safe_sink_reset_n   (auto_out_r_safe_sink_reset_n)
  );
  AsyncQueueSink_4 bundleIn_0_b_sink (	// @[AsyncQueue.scala:207:22]
    .clock                        (clock),
    .reset                        (reset),
    .io_deq_ready                 (auto_in_b_ready),
    .io_async_mem_0_id            (auto_out_b_mem_0_id),
    .io_async_mem_0_resp          (auto_out_b_mem_0_resp),
    .io_async_mem_1_id            (auto_out_b_mem_1_id),
    .io_async_mem_1_resp          (auto_out_b_mem_1_resp),
    .io_async_mem_2_id            (auto_out_b_mem_2_id),
    .io_async_mem_2_resp          (auto_out_b_mem_2_resp),
    .io_async_mem_3_id            (auto_out_b_mem_3_id),
    .io_async_mem_3_resp          (auto_out_b_mem_3_resp),
    .io_async_mem_4_id            (auto_out_b_mem_4_id),
    .io_async_mem_4_resp          (auto_out_b_mem_4_resp),
    .io_async_mem_5_id            (auto_out_b_mem_5_id),
    .io_async_mem_5_resp          (auto_out_b_mem_5_resp),
    .io_async_mem_6_id            (auto_out_b_mem_6_id),
    .io_async_mem_6_resp          (auto_out_b_mem_6_resp),
    .io_async_mem_7_id            (auto_out_b_mem_7_id),
    .io_async_mem_7_resp          (auto_out_b_mem_7_resp),
    .io_async_widx                (auto_out_b_widx),
    .io_async_safe_widx_valid     (auto_out_b_safe_widx_valid),
    .io_async_safe_source_reset_n (auto_out_b_safe_source_reset_n),
    .io_deq_valid                 (auto_in_b_valid),
    .io_deq_bits_id               (auto_in_b_bits_id),
    .io_deq_bits_resp             (auto_in_b_bits_resp),
    .io_async_ridx                (auto_out_b_ridx),
    .io_async_safe_ridx_valid     (auto_out_b_safe_ridx_valid),
    .io_async_safe_sink_reset_n   (auto_out_b_safe_sink_reset_n)
  );
endmodule

