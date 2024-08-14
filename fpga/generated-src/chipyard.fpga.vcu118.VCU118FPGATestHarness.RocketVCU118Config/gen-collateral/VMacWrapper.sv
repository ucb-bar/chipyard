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

module VMacWrapper(
  input          clock,
                 io_in_valid,
  input  [5:0]   io_in_bits_uop_ctrl_funct6,
  input  [2:0]   io_in_bits_uop_ctrl_funct3,
  input          io_in_bits_uop_ctrl_vm,
  input  [4:0]   io_in_bits_uop_ctrl_vs1_imm,
  input          io_in_bits_uop_ctrl_widen,
                 io_in_bits_uop_info_ma,
                 io_in_bits_uop_info_ta,
  input  [2:0]   io_in_bits_uop_info_vsew,
  input  [7:0]   io_in_bits_uop_info_vl,
  input  [6:0]   io_in_bits_uop_info_vstart,
  input  [1:0]   io_in_bits_uop_info_vxrm,
  input  [2:0]   io_in_bits_uop_uopIdx,
  input  [127:0] io_in_bits_vs1,
                 io_in_bits_vs2,
  input  [63:0]  io_in_bits_rs1,
  input  [127:0] io_in_bits_oldVd,
                 io_in_bits_mask,
  output         io_out_valid,
  output [127:0] io_out_bits_vd,
  output         io_out_bits_vxsat
);

  VMac vMac (	// @[VMacWrapper.scala:20:20]
    .clock                       (clock),
    .io_in_valid                 (io_in_valid),
    .io_in_bits_uop_ctrl_funct6  (io_in_bits_uop_ctrl_funct6),
    .io_in_bits_uop_ctrl_funct3  (io_in_bits_uop_ctrl_funct3),
    .io_in_bits_uop_ctrl_vm      (io_in_bits_uop_ctrl_vm),
    .io_in_bits_uop_ctrl_vs1_imm (io_in_bits_uop_ctrl_vs1_imm),
    .io_in_bits_uop_ctrl_widen   (io_in_bits_uop_ctrl_widen),
    .io_in_bits_uop_info_ma      (io_in_bits_uop_info_ma),
    .io_in_bits_uop_info_ta      (io_in_bits_uop_info_ta),
    .io_in_bits_uop_info_vsew    (io_in_bits_uop_info_vsew),
    .io_in_bits_uop_info_vl      (io_in_bits_uop_info_vl),
    .io_in_bits_uop_info_vstart  (io_in_bits_uop_info_vstart),
    .io_in_bits_uop_info_vxrm    (io_in_bits_uop_info_vxrm),
    .io_in_bits_uop_uopIdx       (io_in_bits_uop_uopIdx),
    .io_in_bits_vs1              (io_in_bits_vs1),
    .io_in_bits_vs2              (io_in_bits_vs2),
    .io_in_bits_rs1              (io_in_bits_rs1),
    .io_in_bits_oldVd            (io_in_bits_oldVd),
    .io_in_bits_mask             (io_in_bits_mask),
    .io_out_valid                (io_out_valid),
    .io_out_bits_vd              (io_out_bits_vd),
    .io_out_bits_vxsat           (io_out_bits_vxsat)
  );
endmodule

