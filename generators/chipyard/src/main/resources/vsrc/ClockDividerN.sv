// See LICENSE for license details.

/**
  * An unsynthesizable divide-by-N clock divider.
  * Duty cycle is 100 * (ceil(DIV / 2)) / DIV.
  */

module ClockDividerN #(parameter DIV = 1)(output logic clk_out = 1'b0, input clk_in);

    localparam CWIDTH = $clog2(DIV);
    localparam LOW_CYCLES = DIV / 2;
    localparam HIGH_TRANSITION = LOW_CYCLES - 1;
    localparam LOW_TRANSITION = DIV - 1;

    generate
        if (DIV == 1) begin
            // This needs to be procedural because of the assignment on declaration
            always @(clk_in) begin
                clk_out = clk_in;
            end
        end else begin
            reg [CWIDTH - 1: 0] count = HIGH_TRANSITION[CWIDTH-1:0];
            // The blocking assignment to clock out is used to conform what was done
            // in RC's clock dividers.
            // It should have the effect of preventing registers in the divided clock
            // domain latching register updates launched by the fast clock-domain edge
            // that occurs at the same simulated time (as the divided clock edge).
            always @(posedge clk_in) begin
                if (count == LOW_TRANSITION[CWIDTH-1:0]) begin
                    clk_out = 1'b0;
                    count <= '0;
                end
                else begin
                    if (count == HIGH_TRANSITION[CWIDTH-1:0]) begin
                        clk_out = 1'b1;
                    end
                    count <= count + 1'b1;
                end
            end
        end
    endgenerate
endmodule // ClockDividerN
