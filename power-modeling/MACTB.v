module MACTB();
  reg clock = 0;
  always #(`CLOCK_HALF_PERIOD) clock <= ~clock;
  reg reset = 0;
  reg [31:0] a, b, c, out;

  MAC mac (
    .clock(clock),
    .reset(reset),
    .io_a(a), // allow verilog truncation as usual
    .io_b(b),
    .io_c(c),
    .io_out(out)
  );

  initial begin
    /*
    if ($value$plusargs("CLOCK_PERIOD=%f", clock_period)) begin
      $display("Using CLOCK_PERIOD = ", clock_period);
    end else begin
      $display("Define CLOCK_PERIOD plusarg");
      $finish();
    end
    */
    $vcdpluson;
    @(posedge clock); #(0.01);
    reset = 1;
    @(posedge clock); #(0.01);
    reset = 0;
    repeat (100) begin
      b <= $urandom();
      repeat (16) begin // model weight stationary b argument
        a <= $urandom();
        c <= $urandom();
        @(posedge clock); #(0.01);
      end
    end
    $vcdplusoff;
    $finish();
  end
endmodule

