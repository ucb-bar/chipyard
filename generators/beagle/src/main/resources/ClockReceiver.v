`ifndef SYNTHESIS
module ClockReceiverVerilogModule(
  input VIN,
  input VIP,
  output VOBUF );

  assign VOBUF = (VIN ^ VIP) ? VIP : 1'b0;

endmodule
`endif
