`timescale 1ns/1ns
module MACTBTop();
  reg clock = 0;
  always #10 clock <= ~clock;
  reg reset = 0;

  MACTB tb (
    .clock(clock),
    .reset(reset)
  );

  initial begin
    $vcdpluson;
    @(posedge clock); #1;
    reset = 1;
    @(posedge clock); #1;
    reset = 0;
    repeat (1000) @(posedge clock);
    $vcdplusoff;
    $finish();
  end
endmodule

