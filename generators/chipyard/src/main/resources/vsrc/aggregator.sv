/* verilator lint_off MULTITOP */
/// =================== Unsigned, Fixed Point =========================
module std_fp_add #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  logic [WIDTH-1:0] left,
    input  logic [WIDTH-1:0] right,
    output logic [WIDTH-1:0] out
);
  assign out = left + right;
endmodule

module std_fp_sub #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  logic [WIDTH-1:0] left,
    input  logic [WIDTH-1:0] right,
    output logic [WIDTH-1:0] out
);
  assign out = left - right;
endmodule

module std_fp_mult_pipe #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16,
    parameter SIGNED = 0
) (
    input  logic [WIDTH-1:0] left,
    input  logic [WIDTH-1:0] right,
    input  logic             go,
    input  logic             clk,
    input  logic             reset,
    output logic [WIDTH-1:0] out,
    output logic             done
);
  logic [WIDTH-1:0]          rtmp;
  logic [WIDTH-1:0]          ltmp;
  logic [(WIDTH << 1) - 1:0] out_tmp;
  // Buffer used to walk through the 3 cycles of the pipeline.
  logic done_buf[1:0];

  assign done = done_buf[1];

  assign out = out_tmp[(WIDTH << 1) - INT_WIDTH - 1 : WIDTH - INT_WIDTH];

  // If the done buffer is completely empty and go is high then execution
  // just started.
  logic start;
  assign start = go;

  // Start sending the done signal.
  always_ff @(posedge clk) begin
    if (start)
      done_buf[0] <= 1;
    else
      done_buf[0] <= 0;
  end

  // Push the done signal through the pipeline.
  always_ff @(posedge clk) begin
    if (go) begin
      done_buf[1] <= done_buf[0];
    end else begin
      done_buf[1] <= 0;
    end
  end

  // Register the inputs
  always_ff @(posedge clk) begin
    if (reset) begin
      rtmp <= 0;
      ltmp <= 0;
    end else if (go) begin
      if (SIGNED) begin
        rtmp <= $signed(right);
        ltmp <= $signed(left);
      end else begin
        rtmp <= right;
        ltmp <= left;
      end
    end else begin
      rtmp <= 0;
      ltmp <= 0;
    end

  end

  // Compute the output and save it into out_tmp
  always_ff @(posedge clk) begin
    if (reset) begin
      out_tmp <= 0;
    end else if (go) begin
      if (SIGNED) begin
        // In the first cycle, this performs an invalid computation because
        // ltmp and rtmp only get their actual values in cycle 1
        out_tmp <= $signed(
          { {WIDTH{ltmp[WIDTH-1]}}, ltmp} *
          { {WIDTH{rtmp[WIDTH-1]}}, rtmp}
        );
      end else begin
        out_tmp <= ltmp * rtmp;
      end
    end else begin
      out_tmp <= out_tmp;
    end
  end
endmodule

/* verilator lint_off WIDTH */
module std_fp_div_pipe #(
  parameter WIDTH = 32,
  parameter INT_WIDTH = 16,
  parameter FRAC_WIDTH = 16
) (
    input  logic             go,
    input  logic             clk,
    input  logic             reset,
    input  logic [WIDTH-1:0] left,
    input  logic [WIDTH-1:0] right,
    output logic [WIDTH-1:0] out_remainder,
    output logic [WIDTH-1:0] out_quotient,
    output logic             done
);
    localparam ITERATIONS = WIDTH + FRAC_WIDTH;

    logic [WIDTH-1:0] quotient, quotient_next;
    logic [WIDTH:0] acc, acc_next;
    logic [$clog2(ITERATIONS)-1:0] idx;
    logic start, running, finished, dividend_is_zero;

    assign start = go && !running;
    assign dividend_is_zero = start && left == 0;
    assign finished = idx == ITERATIONS - 1 && running;

    always_ff @(posedge clk) begin
      if (reset || finished || dividend_is_zero)
        running <= 0;
      else if (start)
        running <= 1;
      else
        running <= running;
    end

    always_comb begin
      if (acc >= {1'b0, right}) begin
        acc_next = acc - right;
        {acc_next, quotient_next} = {acc_next[WIDTH-1:0], quotient, 1'b1};
      end else begin
        {acc_next, quotient_next} = {acc, quotient} << 1;
      end
    end

    // `done` signaling
    always_ff @(posedge clk) begin
      if (dividend_is_zero || finished)
        done <= 1;
      else
        done <= 0;
    end

    always_ff @(posedge clk) begin
      if (running)
        idx <= idx + 1;
      else
        idx <= 0;
    end

    always_ff @(posedge clk) begin
      if (reset) begin
        out_quotient <= 0;
        out_remainder <= 0;
      end else if (start) begin
        out_quotient <= 0;
        out_remainder <= left;
      end else if (go == 0) begin
        out_quotient <= out_quotient;
        out_remainder <= out_remainder;
      end else if (dividend_is_zero) begin
        out_quotient <= 0;
        out_remainder <= 0;
      end else if (finished) begin
        out_quotient <= quotient_next;
        out_remainder <= out_remainder;
      end else begin
        out_quotient <= out_quotient;
        if (right <= out_remainder)
          out_remainder <= out_remainder - right;
        else
          out_remainder <= out_remainder;
      end
    end

    always_ff @(posedge clk) begin
      if (reset) begin
        acc <= 0;
        quotient <= 0;
      end else if (start) begin
        {acc, quotient} <= {{WIDTH{1'b0}}, left, 1'b0};
      end else begin
        acc <= acc_next;
        quotient <= quotient_next;
      end
    end
endmodule

module std_fp_gt #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  logic [WIDTH-1:0] left,
    input  logic [WIDTH-1:0] right,
    output logic             out
);
  assign out = left > right;
endmodule

/// =================== Signed, Fixed Point =========================
module std_fp_sadd #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out
);
  assign out = $signed(left + right);
endmodule

module std_fp_ssub #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out
);

  assign out = $signed(left - right);
endmodule

module std_fp_smult_pipe #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  [WIDTH-1:0]              left,
    input  [WIDTH-1:0]              right,
    input  logic                    reset,
    input  logic                    go,
    input  logic                    clk,
    output logic [WIDTH-1:0]        out,
    output logic                    done
);
  std_fp_mult_pipe #(
    .WIDTH(WIDTH),
    .INT_WIDTH(INT_WIDTH),
    .FRAC_WIDTH(FRAC_WIDTH),
    .SIGNED(1)
  ) comp (
    .clk(clk),
    .done(done),
    .reset(reset),
    .go(go),
    .left(left),
    .right(right),
    .out(out)
  );
endmodule

module std_fp_sdiv_pipe #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input                     clk,
    input                     go,
    input                     reset,
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out_quotient,
    output signed [WIDTH-1:0] out_remainder,
    output logic              done
);

  logic signed [WIDTH-1:0] left_abs, right_abs, comp_out_q, comp_out_r, right_save, out_rem_intermediate;

  // Registers to figure out how to transform outputs.
  logic different_signs, left_sign, right_sign;

  // Latch the value of control registers so that their available after
  // go signal becomes low.
  always_ff @(posedge clk) begin
    if (go) begin
      right_save <= right_abs;
      left_sign <= left[WIDTH-1];
      right_sign <= right[WIDTH-1];
    end else begin
      left_sign <= left_sign;
      right_save <= right_save;
      right_sign <= right_sign;
    end
  end

  assign right_abs = right[WIDTH-1] ? -right : right;
  assign left_abs = left[WIDTH-1] ? -left : left;

  assign different_signs = left_sign ^ right_sign;
  assign out_quotient = different_signs ? -comp_out_q : comp_out_q;

  // Remainder is computed as:
  //  t0 = |left| % |right|
  //  t1 = if left * right < 0 and t0 != 0 then |right| - t0 else t0
  //  rem = if right < 0 then -t1 else t1
  assign out_rem_intermediate = different_signs & |comp_out_r ? $signed(right_save - comp_out_r) : comp_out_r;
  assign out_remainder = right_sign ? -out_rem_intermediate : out_rem_intermediate;

  std_fp_div_pipe #(
    .WIDTH(WIDTH),
    .INT_WIDTH(INT_WIDTH),
    .FRAC_WIDTH(FRAC_WIDTH)
  ) comp (
    .reset(reset),
    .clk(clk),
    .done(done),
    .go(go),
    .left(left_abs),
    .right(right_abs),
    .out_quotient(comp_out_q),
    .out_remainder(comp_out_r)
  );
endmodule

module std_fp_sgt #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
    input  logic signed [WIDTH-1:0] left,
    input  logic signed [WIDTH-1:0] right,
    output logic signed             out
);
  assign out = $signed(left > right);
endmodule

module std_fp_slt #(
    parameter WIDTH = 32,
    parameter INT_WIDTH = 16,
    parameter FRAC_WIDTH = 16
) (
   input logic signed [WIDTH-1:0] left,
   input logic signed [WIDTH-1:0] right,
   output logic signed            out
);
  assign out = $signed(left < right);
endmodule

/// =================== Unsigned, Bitnum =========================
module std_mult_pipe #(
    parameter WIDTH = 32
) (
    input  logic [WIDTH-1:0] left,
    input  logic [WIDTH-1:0] right,
    input  logic             reset,
    input  logic             go,
    input  logic             clk,
    output logic [WIDTH-1:0] out,
    output logic             done
);
  std_fp_mult_pipe #(
    .WIDTH(WIDTH),
    .INT_WIDTH(WIDTH),
    .FRAC_WIDTH(0),
    .SIGNED(0)
  ) comp (
    .reset(reset),
    .clk(clk),
    .done(done),
    .go(go),
    .left(left),
    .right(right),
    .out(out)
  );
endmodule

module std_div_pipe #(
    parameter WIDTH = 32
) (
    input                    reset,
    input                    clk,
    input                    go,
    input        [WIDTH-1:0] left,
    input        [WIDTH-1:0] right,
    output logic [WIDTH-1:0] out_remainder,
    output logic [WIDTH-1:0] out_quotient,
    output logic             done
);

  logic [WIDTH-1:0] dividend;
  logic [(WIDTH-1)*2:0] divisor;
  logic [WIDTH-1:0] quotient;
  logic [WIDTH-1:0] quotient_msk;
  logic start, running, finished, dividend_is_zero;

  assign start = go && !running;
  assign finished = quotient_msk == 0 && running;
  assign dividend_is_zero = start && left == 0;

  always_ff @(posedge clk) begin
    // Early return if the divisor is zero.
    if (finished || dividend_is_zero)
      done <= 1;
    else
      done <= 0;
  end

  always_ff @(posedge clk) begin
    if (reset || finished || dividend_is_zero)
      running <= 0;
    else if (start)
      running <= 1;
    else
      running <= running;
  end

  // Outputs
  always_ff @(posedge clk) begin
    if (dividend_is_zero || start) begin
      out_quotient <= 0;
      out_remainder <= 0;
    end else if (finished) begin
      out_quotient <= quotient;
      out_remainder <= dividend;
    end else begin
      // Otherwise, explicitly latch the values.
      out_quotient <= out_quotient;
      out_remainder <= out_remainder;
    end
  end

  // Calculate the quotient mask.
  always_ff @(posedge clk) begin
    if (start)
      quotient_msk <= 1 << WIDTH - 1;
    else if (running)
      quotient_msk <= quotient_msk >> 1;
    else
      quotient_msk <= quotient_msk;
  end

  // Calculate the quotient.
  always_ff @(posedge clk) begin
    if (start)
      quotient <= 0;
    else if (divisor <= dividend)
      quotient <= quotient | quotient_msk;
    else
      quotient <= quotient;
  end

  // Calculate the dividend.
  always_ff @(posedge clk) begin
    if (start)
      dividend <= left;
    else if (divisor <= dividend)
      dividend <= dividend - divisor;
    else
      dividend <= dividend;
  end

  always_ff @(posedge clk) begin
    if (start) begin
      divisor <= right << WIDTH - 1;
    end else if (finished) begin
      divisor <= 0;
    end else begin
      divisor <= divisor >> 1;
    end
  end

  // Simulation self test against unsynthesizable implementation.
  `ifdef VERILATOR
    logic [WIDTH-1:0] l, r;
    always_ff @(posedge clk) begin
      if (go) begin
        l <= left;
        r <= right;
      end else begin
        l <= l;
        r <= r;
      end
    end

    always @(posedge clk) begin
      if (done && $unsigned(out_remainder) != $unsigned(l % r))
        $error(
          "\nstd_div_pipe (Remainder): Computed and golden outputs do not match!\n",
          "left: %0d", $unsigned(l),
          "  right: %0d\n", $unsigned(r),
          "expected: %0d", $unsigned(l % r),
          "  computed: %0d", $unsigned(out_remainder)
        );

      if (done && $unsigned(out_quotient) != $unsigned(l / r))
        $error(
          "\nstd_div_pipe (Quotient): Computed and golden outputs do not match!\n",
          "left: %0d", $unsigned(l),
          "  right: %0d\n", $unsigned(r),
          "expected: %0d", $unsigned(l / r),
          "  computed: %0d", $unsigned(out_quotient)
        );
    end
  `endif
endmodule

/// =================== Signed, Bitnum =========================
module std_sadd #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out
);
  assign out = $signed(left + right);
endmodule

module std_ssub #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out
);
  assign out = $signed(left - right);
endmodule

module std_smult_pipe #(
    parameter WIDTH = 32
) (
    input  logic                    reset,
    input  logic                    go,
    input  logic                    clk,
    input  signed       [WIDTH-1:0] left,
    input  signed       [WIDTH-1:0] right,
    output logic signed [WIDTH-1:0] out,
    output logic                    done
);
  std_fp_mult_pipe #(
    .WIDTH(WIDTH),
    .INT_WIDTH(WIDTH),
    .FRAC_WIDTH(0),
    .SIGNED(1)
  ) comp (
    .reset(reset),
    .clk(clk),
    .done(done),
    .go(go),
    .left(left),
    .right(right),
    .out(out)
  );
endmodule

/* verilator lint_off WIDTH */
module std_sdiv_pipe #(
    parameter WIDTH = 32
) (
    input                           reset,
    input                           clk,
    input                           go,
    input  logic signed [WIDTH-1:0] left,
    input  logic signed [WIDTH-1:0] right,
    output logic signed [WIDTH-1:0] out_quotient,
    output logic signed [WIDTH-1:0] out_remainder,
    output logic                    done
);

  logic signed [WIDTH-1:0] left_abs, right_abs, comp_out_q, comp_out_r, right_save, out_rem_intermediate;

  // Registers to figure out how to transform outputs.
  logic different_signs, left_sign, right_sign;

  // Latch the value of control registers so that their available after
  // go signal becomes low.
  always_ff @(posedge clk) begin
    if (go) begin
      right_save <= right_abs;
      left_sign <= left[WIDTH-1];
      right_sign <= right[WIDTH-1];
    end else begin
      left_sign <= left_sign;
      right_save <= right_save;
      right_sign <= right_sign;
    end
  end

  assign right_abs = right[WIDTH-1] ? -right : right;
  assign left_abs = left[WIDTH-1] ? -left : left;

  assign different_signs = left_sign ^ right_sign;
  assign out_quotient = different_signs ? -comp_out_q : comp_out_q;

  // Remainder is computed as:
  //  t0 = |left| % |right|
  //  t1 = if left * right < 0 and t0 != 0 then |right| - t0 else t0
  //  rem = if right < 0 then -t1 else t1
  assign out_rem_intermediate = different_signs & |comp_out_r ? $signed(right_save - comp_out_r) : comp_out_r;
  assign out_remainder = right_sign ? -out_rem_intermediate : out_rem_intermediate;

  std_div_pipe #(
    .WIDTH(WIDTH)
  ) comp (
    .reset(reset),
    .clk(clk),
    .done(done),
    .go(go),
    .left(left_abs),
    .right(right_abs),
    .out_quotient(comp_out_q),
    .out_remainder(comp_out_r)
  );

  // Simulation self test against unsynthesizable implementation.
  `ifdef VERILATOR
    logic signed [WIDTH-1:0] l, r;
    always_ff @(posedge clk) begin
      if (go) begin
        l <= left;
        r <= right;
      end else begin
        l <= l;
        r <= r;
      end
    end

    always @(posedge clk) begin
      if (done && out_quotient != $signed(l / r))
        $error(
          "\nstd_sdiv_pipe (Quotient): Computed and golden outputs do not match!\n",
          "left: %0d", l,
          "  right: %0d\n", r,
          "expected: %0d", $signed(l / r),
          "  computed: %0d", $signed(out_quotient),
        );
      if (done && out_remainder != $signed(((l % r) + r) % r))
        $error(
          "\nstd_sdiv_pipe (Remainder): Computed and golden outputs do not match!\n",
          "left: %0d", l,
          "  right: %0d\n", r,
          "expected: %0d", $signed(((l % r) + r) % r),
          "  computed: %0d", $signed(out_remainder),
        );
    end
  `endif
endmodule

module std_sgt #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed             out
);
  assign out = $signed(left > right);
endmodule

module std_slt #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed             out
);
  assign out = $signed(left < right);
endmodule

module std_seq #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed             out
);
  assign out = $signed(left == right);
endmodule

module std_sneq #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed             out
);
  assign out = $signed(left != right);
endmodule

module std_sge #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed             out
);
  assign out = $signed(left >= right);
endmodule

module std_sle #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed             out
);
  assign out = $signed(left <= right);
endmodule

module std_slsh #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out
);
  assign out = left <<< right;
endmodule

module std_srsh #(
    parameter WIDTH = 32
) (
    input  signed [WIDTH-1:0] left,
    input  signed [WIDTH-1:0] right,
    output signed [WIDTH-1:0] out
);
  assign out = left >>> right;
endmodule

// Signed extension
module std_signext #(
  parameter IN_WIDTH  = 32,
  parameter OUT_WIDTH = 32
) (
  input wire logic [IN_WIDTH-1:0]  in,
  output logic     [OUT_WIDTH-1:0] out
);
  localparam EXTEND = OUT_WIDTH - IN_WIDTH;
  assign out = { {EXTEND {in[IN_WIDTH-1]}}, in};

  `ifdef VERILATOR
    always_comb begin
      if (IN_WIDTH > OUT_WIDTH)
        $error(
          "std_signext: Output width less than input width\n",
          "IN_WIDTH: %0d", IN_WIDTH,
          "OUT_WIDTH: %0d", OUT_WIDTH
        );
    end
  `endif
endmodule

module std_const_mult #(
    parameter WIDTH = 32,
    parameter VALUE = 1
) (
    input  signed [WIDTH-1:0] in,
    output signed [WIDTH-1:0] out
);
  assign out = in * VALUE;
endmodule

module comb_mem_d1 #(
    parameter WIDTH = 32,
    parameter SIZE = 16,
    parameter IDX_SIZE = 4
) (
   input wire                logic [IDX_SIZE-1:0] addr0,
   input wire                logic [ WIDTH-1:0] write_data,
   input wire                logic write_en,
   input wire                logic clk,
   input wire                logic reset,
   output logic [ WIDTH-1:0] read_data,
   output logic              done
);

  logic [WIDTH-1:0] mem[SIZE-1:0];

  /* verilator lint_off WIDTH */
  assign read_data = mem[addr0];

  always_ff @(posedge clk) begin
    if (reset)
      done <= '0;
    else if (write_en)
      done <= '1;
    else
      done <= '0;
  end

  always_ff @(posedge clk) begin
    if (!reset && write_en)
      mem[addr0] <= write_data;
  end

  // Check for out of bounds access
  `ifdef VERILATOR
    always_comb begin
      if (addr0 >= SIZE)
        $error(
          "comb_mem_d1: Out of bounds access\n",
          "addr0: %0d\n", addr0,
          "SIZE: %0d", SIZE
        );
    end
  `endif
endmodule

module comb_mem_d2 #(
    parameter WIDTH = 32,
    parameter D0_SIZE = 16,
    parameter D1_SIZE = 16,
    parameter D0_IDX_SIZE = 4,
    parameter D1_IDX_SIZE = 4
) (
   input wire                logic [D0_IDX_SIZE-1:0] addr0,
   input wire                logic [D1_IDX_SIZE-1:0] addr1,
   input wire                logic [ WIDTH-1:0] write_data,
   input wire                logic write_en,
   input wire                logic clk,
   input wire                logic reset,
   output logic [ WIDTH-1:0] read_data,
   output logic              done
);

  /* verilator lint_off WIDTH */
  logic [WIDTH-1:0] mem[D0_SIZE-1:0][D1_SIZE-1:0];

  assign read_data = mem[addr0][addr1];

  always_ff @(posedge clk) begin
    if (reset)
      done <= '0;
    else if (write_en)
      done <= '1;
    else
      done <= '0;
  end

  always_ff @(posedge clk) begin
    if (!reset && write_en)
      mem[addr0][addr1] <= write_data;
  end

  // Check for out of bounds access
  `ifdef VERILATOR
    always_comb begin
      if (addr0 >= D0_SIZE)
        $error(
          "comb_mem_d2: Out of bounds access\n",
          "addr0: %0d\n", addr0,
          "D0_SIZE: %0d", D0_SIZE
        );
      if (addr1 >= D1_SIZE)
        $error(
          "comb_mem_d2: Out of bounds access\n",
          "addr1: %0d\n", addr1,
          "D1_SIZE: %0d", D1_SIZE
        );
    end
  `endif
endmodule

module comb_mem_d3 #(
    parameter WIDTH = 32,
    parameter D0_SIZE = 16,
    parameter D1_SIZE = 16,
    parameter D2_SIZE = 16,
    parameter D0_IDX_SIZE = 4,
    parameter D1_IDX_SIZE = 4,
    parameter D2_IDX_SIZE = 4
) (
   input wire                logic [D0_IDX_SIZE-1:0] addr0,
   input wire                logic [D1_IDX_SIZE-1:0] addr1,
   input wire                logic [D2_IDX_SIZE-1:0] addr2,
   input wire                logic [ WIDTH-1:0] write_data,
   input wire                logic write_en,
   input wire                logic clk,
   input wire                logic reset,
   output logic [ WIDTH-1:0] read_data,
   output logic              done
);

  /* verilator lint_off WIDTH */
  logic [WIDTH-1:0] mem[D0_SIZE-1:0][D1_SIZE-1:0][D2_SIZE-1:0];

  assign read_data = mem[addr0][addr1][addr2];

  always_ff @(posedge clk) begin
    if (reset)
      done <= '0;
    else if (write_en)
      done <= '1;
    else
      done <= '0;
  end

  always_ff @(posedge clk) begin
    if (!reset && write_en)
      mem[addr0][addr1][addr2] <= write_data;
  end

  // Check for out of bounds access
  `ifdef VERILATOR
    always_comb begin
      if (addr0 >= D0_SIZE)
        $error(
          "comb_mem_d3: Out of bounds access\n",
          "addr0: %0d\n", addr0,
          "D0_SIZE: %0d", D0_SIZE
        );
      if (addr1 >= D1_SIZE)
        $error(
          "comb_mem_d3: Out of bounds access\n",
          "addr1: %0d\n", addr1,
          "D1_SIZE: %0d", D1_SIZE
        );
      if (addr2 >= D2_SIZE)
        $error(
          "comb_mem_d3: Out of bounds access\n",
          "addr2: %0d\n", addr2,
          "D2_SIZE: %0d", D2_SIZE
        );
    end
  `endif
endmodule

module comb_mem_d4 #(
    parameter WIDTH = 32,
    parameter D0_SIZE = 16,
    parameter D1_SIZE = 16,
    parameter D2_SIZE = 16,
    parameter D3_SIZE = 16,
    parameter D0_IDX_SIZE = 4,
    parameter D1_IDX_SIZE = 4,
    parameter D2_IDX_SIZE = 4,
    parameter D3_IDX_SIZE = 4
) (
   input wire                logic [D0_IDX_SIZE-1:0] addr0,
   input wire                logic [D1_IDX_SIZE-1:0] addr1,
   input wire                logic [D2_IDX_SIZE-1:0] addr2,
   input wire                logic [D3_IDX_SIZE-1:0] addr3,
   input wire                logic [ WIDTH-1:0] write_data,
   input wire                logic write_en,
   input wire                logic clk,
   input wire                logic reset,
   output logic [ WIDTH-1:0] read_data,
   output logic              done
);

  /* verilator lint_off WIDTH */
  logic [WIDTH-1:0] mem[D0_SIZE-1:0][D1_SIZE-1:0][D2_SIZE-1:0][D3_SIZE-1:0];

  assign read_data = mem[addr0][addr1][addr2][addr3];

  always_ff @(posedge clk) begin
    if (reset)
      done <= '0;
    else if (write_en)
      done <= '1;
    else
      done <= '0;
  end

  always_ff @(posedge clk) begin
    if (!reset && write_en)
      mem[addr0][addr1][addr2][addr3] <= write_data;
  end

  // Check for out of bounds access
  `ifdef VERILATOR
    always_comb begin
      if (addr0 >= D0_SIZE)
        $error(
          "comb_mem_d4: Out of bounds access\n",
          "addr0: %0d\n", addr0,
          "D0_SIZE: %0d", D0_SIZE
        );
      if (addr1 >= D1_SIZE)
        $error(
          "comb_mem_d4: Out of bounds access\n",
          "addr1: %0d\n", addr1,
          "D1_SIZE: %0d", D1_SIZE
        );
      if (addr2 >= D2_SIZE)
        $error(
          "comb_mem_d4: Out of bounds access\n",
          "addr2: %0d\n", addr2,
          "D2_SIZE: %0d", D2_SIZE
        );
      if (addr3 >= D3_SIZE)
        $error(
          "comb_mem_d4: Out of bounds access\n",
          "addr3: %0d\n", addr3,
          "D3_SIZE: %0d", D3_SIZE
        );
    end
  `endif
endmodule

/**
 * Core primitives for Calyx.
 * Implements core primitives used by the compiler.
 *
 * Conventions:
 * - All parameter names must be SNAKE_CASE and all caps.
 * - Port names must be snake_case, no caps.
 */

module std_slice #(
    parameter IN_WIDTH  = 32,
    parameter OUT_WIDTH = 32
) (
   input wire                   logic [ IN_WIDTH-1:0] in,
   output logic [OUT_WIDTH-1:0] out
);
  assign out = in[OUT_WIDTH-1:0];

  `ifdef VERILATOR
    always_comb begin
      if (IN_WIDTH < OUT_WIDTH)
        $error(
          "std_slice: Input width less than output width\n",
          "IN_WIDTH: %0d", IN_WIDTH,
          "OUT_WIDTH: %0d", OUT_WIDTH
        );
    end
  `endif
endmodule

module std_pad #(
    parameter IN_WIDTH  = 32,
    parameter OUT_WIDTH = 32
) (
   input wire logic [IN_WIDTH-1:0]  in,
   output logic     [OUT_WIDTH-1:0] out
);
  localparam EXTEND = OUT_WIDTH - IN_WIDTH;
  assign out = { {EXTEND {1'b0}}, in};

  `ifdef VERILATOR
    always_comb begin
      if (IN_WIDTH > OUT_WIDTH)
        $error(
          "std_pad: Output width less than input width\n",
          "IN_WIDTH: %0d", IN_WIDTH,
          "OUT_WIDTH: %0d", OUT_WIDTH
        );
    end
  `endif
endmodule

module std_cat #(
  parameter LEFT_WIDTH  = 32,
  parameter RIGHT_WIDTH = 32,
  parameter OUT_WIDTH = 64
) (
  input wire logic [LEFT_WIDTH-1:0] left,
  input wire logic [RIGHT_WIDTH-1:0] right,
  output logic [OUT_WIDTH-1:0] out
);
  assign out = {left, right};

  `ifdef VERILATOR
    always_comb begin
      if (LEFT_WIDTH + RIGHT_WIDTH != OUT_WIDTH)
        $error(
          "std_cat: Output width must equal sum of input widths\n",
          "LEFT_WIDTH: %0d", LEFT_WIDTH,
          "RIGHT_WIDTH: %0d", RIGHT_WIDTH,
          "OUT_WIDTH: %0d", OUT_WIDTH
        );
    end
  `endif
endmodule

module std_not #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] in,
   output logic [WIDTH-1:0] out
);
  assign out = ~in;
endmodule

module std_and #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] left,
   input wire               logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
  assign out = left & right;
endmodule

module std_or #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] left,
   input wire               logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
  assign out = left | right;
endmodule

module std_xor #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] left,
   input wire               logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
  assign out = left ^ right;
endmodule

module std_sub #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] left,
   input wire               logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
  assign out = left - right;
endmodule

module std_gt #(
    parameter WIDTH = 32
) (
   input wire   logic [WIDTH-1:0] left,
   input wire   logic [WIDTH-1:0] right,
   output logic out
);
  assign out = left > right;
endmodule

module std_lt #(
    parameter WIDTH = 32
) (
   input wire   logic [WIDTH-1:0] left,
   input wire   logic [WIDTH-1:0] right,
   output logic out
);
  assign out = left < right;
endmodule

module std_eq #(
    parameter WIDTH = 32
) (
   input wire   logic [WIDTH-1:0] left,
   input wire   logic [WIDTH-1:0] right,
   output logic out
);
  assign out = left == right;
endmodule

module std_neq #(
    parameter WIDTH = 32
) (
   input wire   logic [WIDTH-1:0] left,
   input wire   logic [WIDTH-1:0] right,
   output logic out
);
  assign out = left != right;
endmodule

module std_ge #(
    parameter WIDTH = 32
) (
    input wire   logic [WIDTH-1:0] left,
    input wire   logic [WIDTH-1:0] right,
    output logic out
);
  assign out = left >= right;
endmodule

module std_le #(
    parameter WIDTH = 32
) (
   input wire   logic [WIDTH-1:0] left,
   input wire   logic [WIDTH-1:0] right,
   output logic out
);
  assign out = left <= right;
endmodule

module std_lsh #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] left,
   input wire               logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
  assign out = left << right;
endmodule

module std_rsh #(
    parameter WIDTH = 32
) (
   input wire               logic [WIDTH-1:0] left,
   input wire               logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
  assign out = left >> right;
endmodule

/// this primitive is intended to be used
/// for lowering purposes (not in source programs)
module std_mux #(
    parameter WIDTH = 32
) (
   input wire               logic cond,
   input wire               logic [WIDTH-1:0] tru,
   input wire               logic [WIDTH-1:0] fal,
   output logic [WIDTH-1:0] out
);
  assign out = cond ? tru : fal;
endmodule

module std_bit_slice #(
    parameter IN_WIDTH = 32,
    parameter START_IDX = 0,
    parameter END_IDX = 31,
    parameter OUT_WIDTH = 32
)(
   input wire logic [IN_WIDTH-1:0] in,
   output logic [OUT_WIDTH-1:0] out
);
    assign out = in[END_IDX:START_IDX];

  `ifdef VERILATOR
    always_comb begin
      if (START_IDX < 0 || END_IDX > IN_WIDTH-1)
        $error(
          "std_bit_slice: Slice range out of bounds\n",
          "IN_WIDTH: %0d", IN_WIDTH,
          "START_IDX: %0d", START_IDX,
          "END_IDX: %0d", END_IDX,
        );
    end
  `endif

endmodule

module undef #(
    parameter WIDTH = 32
) (
   output logic [WIDTH-1:0] out
);
assign out = 'x;
endmodule

module std_const #(
    parameter WIDTH = 32,
    parameter VALUE = 32
) (
   output logic [WIDTH-1:0] out
);
assign out = VALUE;
endmodule

module std_wire #(
    parameter WIDTH = 32
) (
   input wire logic [WIDTH-1:0] in,
   output logic [WIDTH-1:0] out
);
assign out = in;
endmodule

module std_add #(
    parameter WIDTH = 32
) (
   input wire logic [WIDTH-1:0] left,
   input wire logic [WIDTH-1:0] right,
   output logic [WIDTH-1:0] out
);
assign out = left + right;
endmodule

module std_reg #(
    parameter WIDTH = 32
) (
   input wire logic [WIDTH-1:0] in,
   input wire logic write_en,
   input wire logic clk,
   input wire logic reset,
   output logic [WIDTH-1:0] out,
   output logic done
);
always_ff @(posedge clk) begin
    if (reset) begin
       out <= 0;
       done <= 0;
    end else if (write_en) begin
      out <= in;
      done <= 1'd1;
    end else done <= 1'd0;
  end
endmodule

module CalyxSumBlackBox(
  input logic [3:0] in,
  output logic [3:0] out,
  input logic go,
  input logic clk,
  input logic reset,
  output logic done
);
// COMPONENT START: aggregator
logic [3:0] sum_in;
logic sum_write_en;
logic sum_clk;
logic sum_reset;
logic [3:0] sum_out;
logic sum_done;
logic [1:0] cntr_in;
logic cntr_write_en;
logic cntr_clk;
logic cntr_reset;
logic [1:0] cntr_out;
logic cntr_done;
logic [1:0] cntr_add_left;
logic [1:0] cntr_add_right;
logic [1:0] cntr_add_out;
logic [1:0] lt_left;
logic [1:0] lt_right;
logic lt_out;
logic [3:0] add_left;
logic [3:0] add_right;
logic [3:0] add_out;
logic comb_reg_in;
logic comb_reg_write_en;
logic comb_reg_clk;
logic comb_reg_reset;
logic comb_reg_out;
logic comb_reg_done;
logic [1:0] fsm_in;
logic fsm_write_en;
logic fsm_clk;
logic fsm_reset;
logic [1:0] fsm_out;
logic fsm_done;
logic ud_out;
logic [1:0] adder_left;
logic [1:0] adder_right;
logic [1:0] adder_out;
logic ud0_out;
logic [1:0] adder0_left;
logic [1:0] adder0_right;
logic [1:0] adder0_out;
logic signal_reg_in;
logic signal_reg_write_en;
logic signal_reg_clk;
logic signal_reg_reset;
logic signal_reg_out;
logic signal_reg_done;
logic [1:0] fsm0_in;
logic fsm0_write_en;
logic fsm0_clk;
logic fsm0_reset;
logic [1:0] fsm0_out;
logic fsm0_done;
logic init_go_in;
logic init_go_out;
logic init_done_in;
logic init_done_out;
logic early_reset_cond0_go_in;
logic early_reset_cond0_go_out;
logic early_reset_cond0_done_in;
logic early_reset_cond0_done_out;
logic early_reset_static_seq_go_in;
logic early_reset_static_seq_go_out;
logic early_reset_static_seq_done_in;
logic early_reset_static_seq_done_out;
logic wrapper_early_reset_cond0_go_in;
logic wrapper_early_reset_cond0_go_out;
logic wrapper_early_reset_cond0_done_in;
logic wrapper_early_reset_cond0_done_out;
logic while_wrapper_early_reset_static_seq_go_in;
logic while_wrapper_early_reset_static_seq_go_out;
logic while_wrapper_early_reset_static_seq_done_in;
logic while_wrapper_early_reset_static_seq_done_out;
logic tdcc_go_in;
logic tdcc_go_out;
logic tdcc_done_in;
logic tdcc_done_out;
std_reg # (
    .WIDTH(4)
) sum (
    .clk(sum_clk),
    .done(sum_done),
    .in(sum_in),
    .out(sum_out),
    .reset(sum_reset),
    .write_en(sum_write_en)
);
std_reg # (
    .WIDTH(2)
) cntr (
    .clk(cntr_clk),
    .done(cntr_done),
    .in(cntr_in),
    .out(cntr_out),
    .reset(cntr_reset),
    .write_en(cntr_write_en)
);
std_add # (
    .WIDTH(2)
) cntr_add (
    .left(cntr_add_left),
    .out(cntr_add_out),
    .right(cntr_add_right)
);
std_lt # (
    .WIDTH(2)
) lt (
    .left(lt_left),
    .out(lt_out),
    .right(lt_right)
);
std_add # (
    .WIDTH(4)
) add (
    .left(add_left),
    .out(add_out),
    .right(add_right)
);
std_reg # (
    .WIDTH(1)
) comb_reg (
    .clk(comb_reg_clk),
    .done(comb_reg_done),
    .in(comb_reg_in),
    .out(comb_reg_out),
    .reset(comb_reg_reset),
    .write_en(comb_reg_write_en)
);
std_reg # (
    .WIDTH(2)
) fsm (
    .clk(fsm_clk),
    .done(fsm_done),
    .in(fsm_in),
    .out(fsm_out),
    .reset(fsm_reset),
    .write_en(fsm_write_en)
);
undef # (
    .WIDTH(1)
) ud (
    .out(ud_out)
);
std_add # (
    .WIDTH(2)
) adder (
    .left(adder_left),
    .out(adder_out),
    .right(adder_right)
);
undef # (
    .WIDTH(1)
) ud0 (
    .out(ud0_out)
);
std_add # (
    .WIDTH(2)
) adder0 (
    .left(adder0_left),
    .out(adder0_out),
    .right(adder0_right)
);
std_reg # (
    .WIDTH(1)
) signal_reg (
    .clk(signal_reg_clk),
    .done(signal_reg_done),
    .in(signal_reg_in),
    .out(signal_reg_out),
    .reset(signal_reg_reset),
    .write_en(signal_reg_write_en)
);
std_reg # (
    .WIDTH(2)
) fsm0 (
    .clk(fsm0_clk),
    .done(fsm0_done),
    .in(fsm0_in),
    .out(fsm0_out),
    .reset(fsm0_reset),
    .write_en(fsm0_write_en)
);
std_wire # (
    .WIDTH(1)
) init_go (
    .in(init_go_in),
    .out(init_go_out)
);
std_wire # (
    .WIDTH(1)
) init_done (
    .in(init_done_in),
    .out(init_done_out)
);
std_wire # (
    .WIDTH(1)
) early_reset_cond0_go (
    .in(early_reset_cond0_go_in),
    .out(early_reset_cond0_go_out)
);
std_wire # (
    .WIDTH(1)
) early_reset_cond0_done (
    .in(early_reset_cond0_done_in),
    .out(early_reset_cond0_done_out)
);
std_wire # (
    .WIDTH(1)
) early_reset_static_seq_go (
    .in(early_reset_static_seq_go_in),
    .out(early_reset_static_seq_go_out)
);
std_wire # (
    .WIDTH(1)
) early_reset_static_seq_done (
    .in(early_reset_static_seq_done_in),
    .out(early_reset_static_seq_done_out)
);
std_wire # (
    .WIDTH(1)
) wrapper_early_reset_cond0_go (
    .in(wrapper_early_reset_cond0_go_in),
    .out(wrapper_early_reset_cond0_go_out)
);
std_wire # (
    .WIDTH(1)
) wrapper_early_reset_cond0_done (
    .in(wrapper_early_reset_cond0_done_in),
    .out(wrapper_early_reset_cond0_done_out)
);
std_wire # (
    .WIDTH(1)
) while_wrapper_early_reset_static_seq_go (
    .in(while_wrapper_early_reset_static_seq_go_in),
    .out(while_wrapper_early_reset_static_seq_go_out)
);
std_wire # (
    .WIDTH(1)
) while_wrapper_early_reset_static_seq_done (
    .in(while_wrapper_early_reset_static_seq_done_in),
    .out(while_wrapper_early_reset_static_seq_done_out)
);
std_wire # (
    .WIDTH(1)
) tdcc_go (
    .in(tdcc_go_in),
    .out(tdcc_go_out)
);
std_wire # (
    .WIDTH(1)
) tdcc_done (
    .in(tdcc_done_in),
    .out(tdcc_done_out)
);
wire _guard0 = 1;
wire _guard1 = tdcc_done_out;
wire _guard2 = early_reset_cond0_go_out;
wire _guard3 = early_reset_static_seq_go_out;
wire _guard4 = _guard2 | _guard3;
wire _guard5 = fsm_out != 2'd0;
wire _guard6 = early_reset_cond0_go_out;
wire _guard7 = _guard5 & _guard6;
wire _guard8 = fsm_out == 2'd0;
wire _guard9 = early_reset_cond0_go_out;
wire _guard10 = _guard8 & _guard9;
wire _guard11 = fsm_out == 2'd1;
wire _guard12 = early_reset_static_seq_go_out;
wire _guard13 = _guard11 & _guard12;
wire _guard14 = _guard10 | _guard13;
wire _guard15 = fsm_out != 2'd1;
wire _guard16 = early_reset_static_seq_go_out;
wire _guard17 = _guard15 & _guard16;
wire _guard18 = early_reset_cond0_go_out;
wire _guard19 = early_reset_cond0_go_out;
wire _guard20 = init_done_out;
wire _guard21 = ~_guard20;
wire _guard22 = fsm0_out == 2'd0;
wire _guard23 = _guard21 & _guard22;
wire _guard24 = tdcc_go_out;
wire _guard25 = _guard23 & _guard24;
wire _guard26 = early_reset_cond0_go_out;
wire _guard27 = fsm_out == 2'd1;
wire _guard28 = early_reset_static_seq_go_out;
wire _guard29 = _guard27 & _guard28;
wire _guard30 = _guard26 | _guard29;
wire _guard31 = early_reset_cond0_go_out;
wire _guard32 = fsm_out == 2'd1;
wire _guard33 = early_reset_static_seq_go_out;
wire _guard34 = _guard32 & _guard33;
wire _guard35 = _guard31 | _guard34;
wire _guard36 = wrapper_early_reset_cond0_go_out;
wire _guard37 = fsm_out == 2'd0;
wire _guard38 = signal_reg_out;
wire _guard39 = _guard37 & _guard38;
wire _guard40 = fsm_out == 2'd0;
wire _guard41 = early_reset_static_seq_go_out;
wire _guard42 = _guard40 & _guard41;
wire _guard43 = fsm_out == 2'd0;
wire _guard44 = early_reset_static_seq_go_out;
wire _guard45 = _guard43 & _guard44;
wire _guard46 = while_wrapper_early_reset_static_seq_done_out;
wire _guard47 = ~_guard46;
wire _guard48 = fsm0_out == 2'd2;
wire _guard49 = _guard47 & _guard48;
wire _guard50 = tdcc_go_out;
wire _guard51 = _guard49 & _guard50;
wire _guard52 = fsm0_out == 2'd3;
wire _guard53 = fsm0_out == 2'd0;
wire _guard54 = init_done_out;
wire _guard55 = _guard53 & _guard54;
wire _guard56 = tdcc_go_out;
wire _guard57 = _guard55 & _guard56;
wire _guard58 = _guard52 | _guard57;
wire _guard59 = fsm0_out == 2'd1;
wire _guard60 = wrapper_early_reset_cond0_done_out;
wire _guard61 = _guard59 & _guard60;
wire _guard62 = tdcc_go_out;
wire _guard63 = _guard61 & _guard62;
wire _guard64 = _guard58 | _guard63;
wire _guard65 = fsm0_out == 2'd2;
wire _guard66 = while_wrapper_early_reset_static_seq_done_out;
wire _guard67 = _guard65 & _guard66;
wire _guard68 = tdcc_go_out;
wire _guard69 = _guard67 & _guard68;
wire _guard70 = _guard64 | _guard69;
wire _guard71 = fsm0_out == 2'd0;
wire _guard72 = init_done_out;
wire _guard73 = _guard71 & _guard72;
wire _guard74 = tdcc_go_out;
wire _guard75 = _guard73 & _guard74;
wire _guard76 = fsm0_out == 2'd3;
wire _guard77 = fsm0_out == 2'd2;
wire _guard78 = while_wrapper_early_reset_static_seq_done_out;
wire _guard79 = _guard77 & _guard78;
wire _guard80 = tdcc_go_out;
wire _guard81 = _guard79 & _guard80;
wire _guard82 = fsm0_out == 2'd1;
wire _guard83 = wrapper_early_reset_cond0_done_out;
wire _guard84 = _guard82 & _guard83;
wire _guard85 = tdcc_go_out;
wire _guard86 = _guard84 & _guard85;
wire _guard87 = early_reset_static_seq_go_out;
wire _guard88 = early_reset_static_seq_go_out;
wire _guard89 = while_wrapper_early_reset_static_seq_go_out;
wire _guard90 = fsm_out == 2'd0;
wire _guard91 = signal_reg_out;
wire _guard92 = _guard90 & _guard91;
wire _guard93 = fsm_out == 2'd0;
wire _guard94 = signal_reg_out;
wire _guard95 = ~_guard94;
wire _guard96 = _guard93 & _guard95;
wire _guard97 = wrapper_early_reset_cond0_go_out;
wire _guard98 = _guard96 & _guard97;
wire _guard99 = _guard92 | _guard98;
wire _guard100 = fsm_out == 2'd0;
wire _guard101 = signal_reg_out;
wire _guard102 = ~_guard101;
wire _guard103 = _guard100 & _guard102;
wire _guard104 = wrapper_early_reset_cond0_go_out;
wire _guard105 = _guard103 & _guard104;
wire _guard106 = fsm_out == 2'd0;
wire _guard107 = signal_reg_out;
wire _guard108 = _guard106 & _guard107;
wire _guard109 = wrapper_early_reset_cond0_done_out;
wire _guard110 = ~_guard109;
wire _guard111 = fsm0_out == 2'd1;
wire _guard112 = _guard110 & _guard111;
wire _guard113 = tdcc_go_out;
wire _guard114 = _guard112 & _guard113;
wire _guard115 = init_go_out;
wire _guard116 = fsm_out == 2'd0;
wire _guard117 = early_reset_static_seq_go_out;
wire _guard118 = _guard116 & _guard117;
wire _guard119 = _guard115 | _guard118;
wire _guard120 = init_go_out;
wire _guard121 = fsm_out == 2'd0;
wire _guard122 = early_reset_static_seq_go_out;
wire _guard123 = _guard121 & _guard122;
wire _guard124 = fsm_out == 2'd0;
wire _guard125 = early_reset_static_seq_go_out;
wire _guard126 = _guard124 & _guard125;
wire _guard127 = fsm_out == 2'd0;
wire _guard128 = early_reset_static_seq_go_out;
wire _guard129 = _guard127 & _guard128;
wire _guard130 = fsm0_out == 2'd3;
wire _guard131 = early_reset_cond0_go_out;
wire _guard132 = fsm_out == 2'd1;
wire _guard133 = early_reset_static_seq_go_out;
wire _guard134 = _guard132 & _guard133;
wire _guard135 = _guard131 | _guard134;
wire _guard136 = early_reset_cond0_go_out;
wire _guard137 = fsm_out == 2'd1;
wire _guard138 = early_reset_static_seq_go_out;
wire _guard139 = _guard137 & _guard138;
wire _guard140 = _guard136 | _guard139;
wire _guard141 = comb_reg_out;
wire _guard142 = ~_guard141;
wire _guard143 = fsm_out == 2'd0;
wire _guard144 = _guard142 & _guard143;
wire _guard145 = init_go_out;
wire _guard146 = fsm_out == 2'd0;
wire _guard147 = early_reset_static_seq_go_out;
wire _guard148 = _guard146 & _guard147;
wire _guard149 = _guard145 | _guard148;
wire _guard150 = fsm_out == 2'd0;
wire _guard151 = early_reset_static_seq_go_out;
wire _guard152 = _guard150 & _guard151;
wire _guard153 = init_go_out;
assign done = _guard1;
assign out = sum_out;
assign fsm_write_en = _guard4;
assign fsm_clk = clk;
assign fsm_reset = reset;
assign fsm_in =
  _guard7 ? adder_out :
  _guard14 ? 2'd0 :
  _guard17 ? adder0_out :
  2'd0;
always_comb begin
  if(~$onehot0({_guard17, _guard14, _guard7})) begin
    $fatal(2, "Multiple assignment to port `fsm.in'.");
end
end
assign adder_left =
  _guard18 ? fsm_out :
  2'd0;
assign adder_right =
  _guard19 ? 2'd1 :
  2'd0;
assign init_go_in = _guard25;
assign comb_reg_write_en = _guard30;
assign comb_reg_clk = clk;
assign comb_reg_reset = reset;
assign comb_reg_in =
  _guard35 ? lt_out :
  1'd0;
assign early_reset_cond0_done_in = ud_out;
assign early_reset_cond0_go_in = _guard36;
assign wrapper_early_reset_cond0_done_in = _guard39;
assign cntr_add_left = cntr_out;
assign cntr_add_right = 2'd1;
assign while_wrapper_early_reset_static_seq_go_in = _guard51;
assign tdcc_go_in = go;
assign fsm0_write_en = _guard70;
assign fsm0_clk = clk;
assign fsm0_reset = reset;
assign fsm0_in =
  _guard75 ? 2'd1 :
  _guard76 ? 2'd0 :
  _guard81 ? 2'd3 :
  _guard86 ? 2'd2 :
  2'd0;
always_comb begin
  if(~$onehot0({_guard86, _guard81, _guard76, _guard75})) begin
    $fatal(2, "Multiple assignment to port `fsm0.in'.");
end
end
assign adder0_left =
  _guard87 ? fsm_out :
  2'd0;
assign adder0_right =
  _guard88 ? 2'd1 :
  2'd0;
assign init_done_in = cntr_done;
assign early_reset_static_seq_go_in = _guard89;
assign signal_reg_write_en = _guard99;
assign signal_reg_clk = clk;
assign signal_reg_reset = reset;
assign signal_reg_in =
  _guard105 ? 1'd1 :
  _guard108 ? 1'd0 :
  1'd0;
always_comb begin
  if(~$onehot0({_guard108, _guard105})) begin
    $fatal(2, "Multiple assignment to port `signal_reg.in'.");
end
end
assign wrapper_early_reset_cond0_go_in = _guard114;
assign sum_write_en = _guard119;
assign sum_clk = clk;
assign sum_reset = reset;
assign sum_in =
  _guard120 ? 4'd0 :
  _guard123 ? add_out :
  'x;
always_comb begin
  if(~$onehot0({_guard123, _guard120})) begin
    $fatal(2, "Multiple assignment to port `sum.in'.");
end
end
assign add_left = sum_out;
assign add_right = in;
assign early_reset_static_seq_done_in = ud0_out;
assign tdcc_done_in = _guard130;
assign lt_left =
  _guard135 ? cntr_out :
  2'd0;
assign lt_right =
  _guard140 ? 2'd3 :
  2'd0;
assign while_wrapper_early_reset_static_seq_done_in = _guard144;
assign cntr_write_en = _guard149;
assign cntr_clk = clk;
assign cntr_reset = reset;
assign cntr_in =
  _guard152 ? cntr_add_out :
  _guard153 ? 2'd0 :
  'x;
always_comb begin
  if(~$onehot0({_guard153, _guard152})) begin
    $fatal(2, "Multiple assignment to port `cntr.in'.");
end
end
// COMPONENT END: aggregator
endmodule
module main(
  input logic go,
  input logic clk,
  input logic reset,
  output logic done
);
// COMPONENT START: main
string DATA;
int CODE;
initial begin
    CODE = $value$plusargs("DATA=%s", DATA);
    $display("DATA (path to meminit files): %s", DATA);
    $readmemh({DATA, "/mem.dat"}, mem.mem);
end
final begin
    $writememh({DATA, "/mem.out"}, mem.mem);
end
logic [3:0] ss_in;
logic [3:0] ss_out;
logic ss_go;
logic ss_clk;
logic ss_reset;
logic ss_done;
logic mem_addr0;
logic [3:0] mem_write_data;
logic mem_write_en;
logic mem_clk;
logic mem_reset;
logic [3:0] mem_read_data;
logic mem_done;
logic [1:0] fsm_in;
logic fsm_write_en;
logic fsm_clk;
logic fsm_reset;
logic [1:0] fsm_out;
logic fsm_done;
logic print_ss_go_in;
logic print_ss_go_out;
logic print_ss_done_in;
logic print_ss_done_out;
logic invoke0_go_in;
logic invoke0_go_out;
logic invoke0_done_in;
logic invoke0_done_out;
logic tdcc_go_in;
logic tdcc_go_out;
logic tdcc_done_in;
logic tdcc_done_out;
aggregator ss (
    .clk(ss_clk),
    .done(ss_done),
    .go(ss_go),
    .in(ss_in),
    .out(ss_out),
    .reset(ss_reset)
);
comb_mem_d1 # (
    .IDX_SIZE(1),
    .SIZE(1),
    .WIDTH(4)
) mem (
    .addr0(mem_addr0),
    .clk(mem_clk),
    .done(mem_done),
    .read_data(mem_read_data),
    .reset(mem_reset),
    .write_data(mem_write_data),
    .write_en(mem_write_en)
);
std_reg # (
    .WIDTH(2)
) fsm (
    .clk(fsm_clk),
    .done(fsm_done),
    .in(fsm_in),
    .out(fsm_out),
    .reset(fsm_reset),
    .write_en(fsm_write_en)
);
std_wire # (
    .WIDTH(1)
) print_ss_go (
    .in(print_ss_go_in),
    .out(print_ss_go_out)
);
std_wire # (
    .WIDTH(1)
) print_ss_done (
    .in(print_ss_done_in),
    .out(print_ss_done_out)
);
std_wire # (
    .WIDTH(1)
) invoke0_go (
    .in(invoke0_go_in),
    .out(invoke0_go_out)
);
std_wire # (
    .WIDTH(1)
) invoke0_done (
    .in(invoke0_done_in),
    .out(invoke0_done_out)
);
std_wire # (
    .WIDTH(1)
) tdcc_go (
    .in(tdcc_go_in),
    .out(tdcc_go_out)
);
std_wire # (
    .WIDTH(1)
) tdcc_done (
    .in(tdcc_done_in),
    .out(tdcc_done_out)
);
wire _guard0 = 1;
wire _guard1 = tdcc_done_out;
wire _guard2 = fsm_out == 2'd2;
wire _guard3 = fsm_out == 2'd0;
wire _guard4 = invoke0_done_out;
wire _guard5 = _guard3 & _guard4;
wire _guard6 = tdcc_go_out;
wire _guard7 = _guard5 & _guard6;
wire _guard8 = _guard2 | _guard7;
wire _guard9 = fsm_out == 2'd1;
wire _guard10 = print_ss_done_out;
wire _guard11 = _guard9 & _guard10;
wire _guard12 = tdcc_go_out;
wire _guard13 = _guard11 & _guard12;
wire _guard14 = _guard8 | _guard13;
wire _guard15 = fsm_out == 2'd0;
wire _guard16 = invoke0_done_out;
wire _guard17 = _guard15 & _guard16;
wire _guard18 = tdcc_go_out;
wire _guard19 = _guard17 & _guard18;
wire _guard20 = fsm_out == 2'd2;
wire _guard21 = fsm_out == 2'd1;
wire _guard22 = print_ss_done_out;
wire _guard23 = _guard21 & _guard22;
wire _guard24 = tdcc_go_out;
wire _guard25 = _guard23 & _guard24;
wire _guard26 = invoke0_go_out;
wire _guard27 = invoke0_go_out;
wire _guard28 = invoke0_done_out;
wire _guard29 = ~_guard28;
wire _guard30 = fsm_out == 2'd0;
wire _guard31 = _guard29 & _guard30;
wire _guard32 = tdcc_go_out;
wire _guard33 = _guard31 & _guard32;
wire _guard34 = fsm_out == 2'd2;
wire _guard35 = print_ss_go_out;
wire _guard36 = print_ss_go_out;
wire _guard37 = print_ss_go_out;
wire _guard38 = print_ss_done_out;
wire _guard39 = ~_guard38;
wire _guard40 = fsm_out == 2'd1;
wire _guard41 = _guard39 & _guard40;
wire _guard42 = tdcc_go_out;
wire _guard43 = _guard41 & _guard42;
assign done = _guard1;
assign fsm_write_en = _guard14;
assign fsm_clk = clk;
assign fsm_reset = reset;
assign fsm_in =
  _guard19 ? 2'd1 :
  _guard20 ? 2'd0 :
  _guard25 ? 2'd2 :
  2'd0;
always_comb begin
  if(~$onehot0({_guard25, _guard20, _guard19})) begin
    $fatal(2, "Multiple assignment to port `fsm.in'.");
end
end
assign ss_clk = clk;
assign ss_go = _guard26;
assign ss_reset = reset;
assign ss_in =
  _guard27 ? 4'd3 :
  4'd0;
assign print_ss_done_in = mem_done;
assign tdcc_go_in = go;
assign invoke0_go_in = _guard33;
assign invoke0_done_in = ss_done;
assign tdcc_done_in = _guard34;
assign mem_write_en = _guard35;
assign mem_clk = clk;
assign mem_addr0 =
  _guard36 ? 1'd0 :
  1'd0;
assign mem_reset = reset;
assign mem_write_data = ss_out;
assign print_ss_go_in = _guard43;
// COMPONENT END: main
endmodule

