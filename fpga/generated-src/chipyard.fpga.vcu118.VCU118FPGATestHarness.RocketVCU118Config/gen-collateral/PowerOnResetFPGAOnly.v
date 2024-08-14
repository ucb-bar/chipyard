(* keep_hierarchy = "yes" *)
module PowerOnResetFPGAOnly(
  input wire clock,
  (* dont_touch = "true" *) output reg power_on_reset
);
  initial begin
    power_on_reset <= 1'b1;
  end
  always @(posedge clock) begin
    power_on_reset <= 1'b0;
  end
endmodule
