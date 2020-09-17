// See LICENSE for license details.

/**
  * An unsynthesizable divide-by-N clock divider.
  * Duty cycle is 100 * (ceil(DIV / 2)) / DIV.
  */

module ClockDividerN #(parameter DIV)(output logic clk_out = 1'b0, input clk_in);

    localparam DIV_COUNTER_WIDTH = $clog2(DIV);
    localparam LOW_CYCLES = DIV / 2;

    generate
        if (DIV == 1) begin
            // This needs to be procedural because of the assignment on declaration
            always @(clk_in) begin
                clk_out = clk_in;
            end
        end else begin
            reg [DIV_COUNTER_WIDTH - 1: 0] count = '0;
            // The blocking assignment to clock out is used to conform what was done
            // in RC's clock dividers.
            // It should have the effect of preventing registers in the divided clock
            // domain latching register updates launched by the fast clock-domain edge
            // that occurs at the same simulated time (as the divided clock edge).
            always @(posedge clk_in) begin
                if (count == (DIV - 1)) begin
                    clk_out = 1'b0;
                    count <= '0;
                end
                else begin
                    if (count == (LOW_CYCLES - 1)) begin
                        clk_out = 1'b1;
                    end
                    count <= count + 1'b1;
                end
            end
        end
    endgenerate
endmodule // ClockDividerN
