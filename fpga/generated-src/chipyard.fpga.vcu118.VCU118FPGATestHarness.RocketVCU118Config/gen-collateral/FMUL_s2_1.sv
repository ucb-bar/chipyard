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

module FMUL_s2_1(
  input          io_in_special_case_valid,
                 io_in_special_case_bits_nan,
                 io_in_special_case_bits_inf,
                 io_in_special_case_bits_inv,
                 io_in_special_case_bits_hasZero,
                 io_in_early_overflow,
                 io_in_prod_sign,
  input  [11:0]  io_in_shift_amt,
                 io_in_exp_shifted,
  input          io_in_may_be_subnormal,
  input  [2:0]   io_in_rm,
  input  [105:0] io_prod,
  output         io_out_special_case_valid,
                 io_out_special_case_bits_nan,
                 io_out_special_case_bits_inf,
                 io_out_special_case_bits_inv,
                 io_out_special_case_bits_hasZero,
                 io_out_raw_out_sign,
  output [11:0]  io_out_raw_out_exp,
  output [160:0] io_out_raw_out_sig,
  output         io_out_early_overflow,
  output [2:0]   io_out_rm
);

  wire [4255:0] _sig_shifted_raw_T = {4150'h0, io_prod} << io_in_shift_amt;	// @[FMUL.scala:154:41]
  wire          exp_is_subnormal = io_in_may_be_subnormal & ~(_sig_shifted_raw_T[160]);	// @[FMUL.scala:154:{41,54}, :155:{49,52,73}]
  wire          no_extra_shift = _sig_shifted_raw_T[160] | exp_is_subnormal;	// @[FMUL.scala:154:{41,54}, :155:{49,73}, :156:55]
  assign io_out_special_case_valid = io_in_special_case_valid;
  assign io_out_special_case_bits_nan = io_in_special_case_bits_nan;
  assign io_out_special_case_bits_inf = io_in_special_case_bits_inf;
  assign io_out_special_case_bits_inv = io_in_special_case_bits_inv;
  assign io_out_special_case_bits_hasZero = io_in_special_case_bits_hasZero;
  assign io_out_raw_out_sign = io_in_prod_sign;
  assign io_out_raw_out_exp = exp_is_subnormal ? 12'h0 : no_extra_shift ? io_in_exp_shifted : io_in_exp_shifted - 12'h1;	// @[FMUL.scala:155:49, :156:55, :158:{26,53,95}]
  assign io_out_raw_out_sig = no_extra_shift ? _sig_shifted_raw_T[160:0] : {_sig_shifted_raw_T[159:0], 1'h0};	// @[Cat.scala:33:92, FMUL.scala:154:{41,54}, :155:52, :156:55, :159:{24,82}]
  assign io_out_early_overflow = io_in_early_overflow;
  assign io_out_rm = io_in_rm;
endmodule

