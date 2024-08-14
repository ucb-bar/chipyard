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

module DivTop(
  input         clock,
                reset,
                io_in_valid,
  input  [4:0]  io_in_bits_uop_ctrl_lsrc_0,
  input         io_in_bits_uop_ctrl_vm,
  input  [5:0]  io_in_bits_uop_ctrl_funct6,
  input  [2:0]  io_in_bits_uop_ctrl_funct3,
  input         io_in_bits_uop_ctrl_narrow_to_1,
  input  [6:0]  io_in_bits_uop_info_vstart,
  input  [7:0]  io_in_bits_uop_info_vl,
  input  [2:0]  io_in_bits_uop_info_frm,
                io_in_bits_uop_info_vsew,
  input         io_in_bits_uop_info_ma,
                io_in_bits_uop_info_ta,
  input  [63:0] io_in_bits_vs1,
                io_in_bits_vs2,
                io_in_bits_old_vd,
                io_in_bits_rs1,
  input  [7:0]  io_in_bits_prestart,
                io_in_bits_mask,
                io_in_bits_tail,
  output        io_in_ready,
                io_out_valid,
  output [63:0] io_out_bits_vd,
  output [4:0]  io_out_bits_fflags
);

  wire        _fdivsqrt_io_in_ready;	// @[DivTop.scala:77:24]
  wire        _fdivsqrt_io_out_valid;	// @[DivTop.scala:77:24]
  wire [63:0] _fdivsqrt_io_out_bits_vd;	// @[DivTop.scala:77:24]
  wire [4:0]  _fdivsqrt_io_out_bits_fflags;	// @[DivTop.scala:77:24]
  wire [63:0] _fdivsqrt_io_out_bits_uop_maskKeep;	// @[DivTop.scala:77:24]
  wire [63:0] _fdivsqrt_io_out_bits_uop_maskOff;	// @[DivTop.scala:77:24]
  wire        _idiv_io_in_ready;	// @[DivTop.scala:76:20]
  wire        _idiv_io_out_valid;	// @[DivTop.scala:76:20]
  wire [63:0] _idiv_io_out_bits_vd;	// @[DivTop.scala:76:20]
  wire [63:0] _idiv_io_out_bits_uop_maskKeep;	// @[DivTop.scala:76:20]
  wire [63:0] _idiv_io_out_bits_uop_maskOff;	// @[DivTop.scala:76:20]
  wire [63:0] _FinputGen_io_out_vs1;	// @[DivTop.scala:73:25]
  wire [63:0] _FinputGen_io_out_vs2;	// @[DivTop.scala:73:25]
  wire [7:0]  _FinputGen_io_out_prestart;	// @[DivTop.scala:73:25]
  wire [7:0]  _FinputGen_io_out_mask;	// @[DivTop.scala:73:25]
  wire [7:0]  _FinputGen_io_out_tail;	// @[DivTop.scala:73:25]
  wire        _FinputGen_io_out_uop_ctrl_vm;	// @[DivTop.scala:73:25]
  wire [2:0]  _FinputGen_io_out_uop_info_frm;	// @[DivTop.scala:73:25]
  wire [63:0] _FinputGen_io_out_uop_maskKeep;	// @[DivTop.scala:73:25]
  wire [63:0] _FinputGen_io_out_uop_maskOff;	// @[DivTop.scala:73:25]
  wire        _FinputGen_io_out_uop_vfpCtrl_isDivSqrt;	// @[DivTop.scala:73:25]
  wire        _FinputGen_io_out_uop_vfpCtrl_isSqrt;	// @[DivTop.scala:73:25]
  wire        _FinputGen_io_out_uop_vfpCtrl_divReverse;	// @[DivTop.scala:73:25]
  wire        _FinputGen_io_out_uop_typeTag;	// @[DivTop.scala:73:25]
  wire [63:0] _inputGen_io_out_vs1;	// @[DivTop.scala:71:24]
  wire [63:0] _inputGen_io_out_vs2;	// @[DivTop.scala:71:24]
  wire [5:0]  _inputGen_io_out_uop_ctrl_funct6;	// @[DivTop.scala:71:24]
  wire [2:0]  _inputGen_io_out_uop_info_vsew;	// @[DivTop.scala:71:24]
  wire [63:0] _inputGen_io_out_uop_maskKeep;	// @[DivTop.scala:71:24]
  wire [63:0] _inputGen_io_out_uop_maskOff;	// @[DivTop.scala:71:24]
  VDivInputGen inputGen (	// @[DivTop.scala:71:24]
    .io_in_uop_ctrl_vm          (io_in_bits_uop_ctrl_vm),
    .io_in_uop_ctrl_funct6      (io_in_bits_uop_ctrl_funct6),
    .io_in_uop_ctrl_funct3      (io_in_bits_uop_ctrl_funct3),
    .io_in_uop_ctrl_narrow_to_1 (io_in_bits_uop_ctrl_narrow_to_1),
    .io_in_uop_info_vstart      (io_in_bits_uop_info_vstart),
    .io_in_uop_info_vl          (io_in_bits_uop_info_vl),
    .io_in_uop_info_vsew        (io_in_bits_uop_info_vsew),
    .io_in_uop_info_ma          (io_in_bits_uop_info_ma),
    .io_in_uop_info_ta          (io_in_bits_uop_info_ta),
    .io_in_vs1                  (io_in_bits_vs1),
    .io_in_vs2                  (io_in_bits_vs2),
    .io_in_old_vd               (io_in_bits_old_vd),
    .io_in_rs1                  (io_in_bits_rs1),
    .io_in_prestart             (io_in_bits_prestart),
    .io_in_mask                 (io_in_bits_mask),
    .io_in_tail                 (io_in_bits_tail),
    .io_out_vs1                 (_inputGen_io_out_vs1),
    .io_out_vs2                 (_inputGen_io_out_vs2),
    .io_out_uop_ctrl_funct6     (_inputGen_io_out_uop_ctrl_funct6),
    .io_out_uop_info_vsew       (_inputGen_io_out_uop_info_vsew),
    .io_out_uop_maskKeep        (_inputGen_io_out_uop_maskKeep),
    .io_out_uop_maskOff         (_inputGen_io_out_uop_maskOff)
  );
  VFInputGen FinputGen (	// @[DivTop.scala:73:25]
    .io_in_uop_ctrl_lsrc_0         (io_in_bits_uop_ctrl_lsrc_0),
    .io_in_uop_ctrl_vm             (io_in_bits_uop_ctrl_vm),
    .io_in_uop_ctrl_funct6         (io_in_bits_uop_ctrl_funct6),
    .io_in_uop_ctrl_funct3         (io_in_bits_uop_ctrl_funct3),
    .io_in_uop_ctrl_narrow_to_1    (io_in_bits_uop_ctrl_narrow_to_1),
    .io_in_uop_info_vstart         (io_in_bits_uop_info_vstart),
    .io_in_uop_info_vl             (io_in_bits_uop_info_vl),
    .io_in_uop_info_frm            (io_in_bits_uop_info_frm),
    .io_in_uop_info_vsew           (io_in_bits_uop_info_vsew),
    .io_in_uop_info_ma             (io_in_bits_uop_info_ma),
    .io_in_uop_info_ta             (io_in_bits_uop_info_ta),
    .io_in_vs1                     (io_in_bits_vs1),
    .io_in_vs2                     (io_in_bits_vs2),
    .io_in_old_vd                  (io_in_bits_old_vd),
    .io_in_rs1                     (io_in_bits_rs1),
    .io_in_prestart                (io_in_bits_prestart),
    .io_in_mask                    (io_in_bits_mask),
    .io_in_tail                    (io_in_bits_tail),
    .io_out_vs1                    (_FinputGen_io_out_vs1),
    .io_out_vs2                    (_FinputGen_io_out_vs2),
    .io_out_prestart               (_FinputGen_io_out_prestart),
    .io_out_mask                   (_FinputGen_io_out_mask),
    .io_out_tail                   (_FinputGen_io_out_tail),
    .io_out_uop_ctrl_vm            (_FinputGen_io_out_uop_ctrl_vm),
    .io_out_uop_info_frm           (_FinputGen_io_out_uop_info_frm),
    .io_out_uop_maskKeep           (_FinputGen_io_out_uop_maskKeep),
    .io_out_uop_maskOff            (_FinputGen_io_out_uop_maskOff),
    .io_out_uop_vfpCtrl_isDivSqrt  (_FinputGen_io_out_uop_vfpCtrl_isDivSqrt),
    .io_out_uop_vfpCtrl_isSqrt     (_FinputGen_io_out_uop_vfpCtrl_isSqrt),
    .io_out_uop_vfpCtrl_divReverse (_FinputGen_io_out_uop_vfpCtrl_divReverse),
    .io_out_uop_typeTag            (_FinputGen_io_out_uop_typeTag)
  );
  VIntSRT16TimeplexDivider idiv (	// @[DivTop.scala:76:20]
    .clock                      (clock),
    .reset                      (reset),
    .io_in_valid                (io_in_valid & io_in_bits_uop_ctrl_funct3[1:0] == 2'h2),	// @[DivTop.scala:82:{35,53,60}]
    .io_in_bits_vs1             (_inputGen_io_out_vs1),	// @[DivTop.scala:71:24]
    .io_in_bits_vs2             (_inputGen_io_out_vs2),	// @[DivTop.scala:71:24]
    .io_in_bits_uop_ctrl_funct6 (_inputGen_io_out_uop_ctrl_funct6),	// @[DivTop.scala:71:24]
    .io_in_bits_uop_info_vsew   (_inputGen_io_out_uop_info_vsew),	// @[DivTop.scala:71:24]
    .io_in_bits_uop_maskKeep    (_inputGen_io_out_uop_maskKeep),	// @[DivTop.scala:71:24]
    .io_in_bits_uop_maskOff     (_inputGen_io_out_uop_maskOff),	// @[DivTop.scala:71:24]
    .io_in_ready                (_idiv_io_in_ready),
    .io_out_valid               (_idiv_io_out_valid),
    .io_out_bits_vd             (_idiv_io_out_bits_vd),
    .io_out_bits_uop_maskKeep   (_idiv_io_out_bits_uop_maskKeep),
    .io_out_bits_uop_maskOff    (_idiv_io_out_bits_uop_maskOff)
  );
  VFDivSqrt fdivsqrt (	// @[DivTop.scala:77:24]
    .clock                             (clock),
    .reset                             (reset),
    .io_in_valid                       (io_in_valid & io_in_bits_uop_ctrl_funct3[1:0] == 2'h1),	// @[DivTop.scala:82:53, :86:{39,64}]
    .io_in_bits_vs1                    (_FinputGen_io_out_vs1),	// @[DivTop.scala:73:25]
    .io_in_bits_vs2                    (_FinputGen_io_out_vs2),	// @[DivTop.scala:73:25]
    .io_in_bits_prestart               (_FinputGen_io_out_prestart),	// @[DivTop.scala:73:25]
    .io_in_bits_mask                   (_FinputGen_io_out_mask),	// @[DivTop.scala:73:25]
    .io_in_bits_tail                   (_FinputGen_io_out_tail),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_ctrl_vm            (_FinputGen_io_out_uop_ctrl_vm),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_info_frm           (_FinputGen_io_out_uop_info_frm),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_maskKeep           (_FinputGen_io_out_uop_maskKeep),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_maskOff            (_FinputGen_io_out_uop_maskOff),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_vfpCtrl_isDivSqrt  (_FinputGen_io_out_uop_vfpCtrl_isDivSqrt),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_vfpCtrl_isSqrt     (_FinputGen_io_out_uop_vfpCtrl_isSqrt),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_vfpCtrl_divReverse (_FinputGen_io_out_uop_vfpCtrl_divReverse),	// @[DivTop.scala:73:25]
    .io_in_bits_uop_typeTag            (_FinputGen_io_out_uop_typeTag),	// @[DivTop.scala:73:25]
    .io_out_ready                      (~_idiv_io_out_valid),	// @[DivTop.scala:76:20, :107:28]
    .io_in_ready                       (_fdivsqrt_io_in_ready),
    .io_out_valid                      (_fdivsqrt_io_out_valid),
    .io_out_bits_vd                    (_fdivsqrt_io_out_bits_vd),
    .io_out_bits_fflags                (_fdivsqrt_io_out_bits_fflags),
    .io_out_bits_uop_maskKeep          (_fdivsqrt_io_out_bits_uop_maskKeep),
    .io_out_bits_uop_maskOff           (_fdivsqrt_io_out_bits_uop_maskOff)
  );
  assign io_in_ready = _idiv_io_in_ready & _fdivsqrt_io_in_ready;	// @[DivTop.scala:76:20, :77:24, :109:35]
  assign io_out_valid = _idiv_io_out_valid | _fdivsqrt_io_out_valid;	// @[DivTop.scala:76:20, :77:24, :103:37]
  assign io_out_bits_vd = _idiv_io_out_valid ? _idiv_io_out_bits_vd & _idiv_io_out_bits_uop_maskKeep | _idiv_io_out_bits_uop_maskOff : _fdivsqrt_io_out_bits_vd & _fdivsqrt_io_out_bits_uop_maskKeep | _fdivsqrt_io_out_bits_uop_maskOff;	// @[DivTop.scala:76:20, :77:24, :91:44, :92:39, :97:40, :98:35, :102:21]
  assign io_out_bits_fflags = _idiv_io_out_valid ? 5'h0 : _fdivsqrt_io_out_bits_fflags;	// @[DivTop.scala:76:20, :77:24, :102:21]
endmodule

