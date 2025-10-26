`ifndef TOPLEVEL
	`define TOPLEVEL TestDriver.testHarness.chiptop0
`endif

module strobe;

integer cmp;

initial begin		
  #10;
  $display("BEFORE ZOIX INJECTION");
  $fs_inject;
  $display("ZOIX INJECTION");
end


always @(negedge `TOPLEVEL.clock_uncore) begin
  cmp = $fs_compare(`TOPLEVEL.axi4_mem_0_bits_ar_bits_addr);
end

endmodule
