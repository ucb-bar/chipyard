module UIntToAnalog_1 (a, b, b_en);
  inout [0:0] a;
  input [0:0] b;
  input b_en;
  assign a = b_en ? b : 1'bz;
endmodule
