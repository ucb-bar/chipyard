`ifndef TOPLEVEL
	`define TOPLEVEL TestDriver.testHarness.chiptop0
`endif


module strobe;

initial begin        
  #10;
  $display("BEFORE ZOIX INJECTION");
  $fs_inject;
  $display("ZOIX INJECTION");
end

always @(negedge `TOPLEVEL.clock_uncore) begin
  $fs_strobe(`TOPLEVEL.system.tile_prci_domain.element_reset_domain_rockettile.core);
end 
endmodule
