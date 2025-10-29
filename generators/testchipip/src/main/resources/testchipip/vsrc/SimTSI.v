import "DPI-C" function int tsi_tick
(
    input int  chip_id,
    input bit  tsi_out_valid,
    output bit tsi_out_ready,
    input int  tsi_out_bits,

    output bit tsi_in_valid,
    input bit  tsi_in_ready,
    output int tsi_in_bits
);

module SimTSI #(parameter CHIPID=0) (
    input         clock,
    input         reset,
    input         tsi_out_valid,
    output        tsi_out_ready,
    input  [31:0] tsi_out_bits,

    output        tsi_in_valid,
    input         tsi_in_ready,
    output [31:0] tsi_in_bits,

    output [31:0] exit
);

    bit __in_valid;
    bit __out_ready;
    int __in_bits;
    int __exit;

    reg __in_valid_reg;
    reg __out_ready_reg;
    reg [31:0] __in_bits_reg;
    reg [31:0] __exit_reg;

    assign tsi_in_valid  = __in_valid_reg;
    assign tsi_in_bits   = __in_bits_reg;
    assign tsi_out_ready = __out_ready_reg;
    assign exit = __exit_reg;

    // Evaluate the signals on the positive edge
    always @(posedge clock) begin
        if (reset) begin
            __in_valid = 0;
            __out_ready = 0;
            __exit = 0;

            __in_valid_reg <= 0;
            __in_bits_reg <= 0;
            __out_ready_reg <= 0;
            __exit_reg <= 0;
        end else begin
            __exit = tsi_tick(
                CHIPID,
                tsi_out_valid,
                __out_ready,
                tsi_out_bits,
                __in_valid,
                tsi_in_ready,
                __in_bits
            );

            __out_ready_reg <= __out_ready;
            __in_valid_reg  <= __in_valid;
            __in_bits_reg   <= __in_bits;
            __exit_reg <= __exit;
        end
    end

endmodule
