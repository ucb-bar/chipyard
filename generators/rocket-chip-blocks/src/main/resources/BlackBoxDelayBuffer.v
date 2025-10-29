// This module is added as a reference only. Please add library
// cells for delay buffers and muxes from the foundry that is fabricating your SoC.

module BlackBoxDelayBuffer ( in, mux_out, out, sel
  );

  input in;
  output mux_out;
  output out;
  input [4:0] sel;

  assign mux_out = in;
  assign out = in;
endmodule
