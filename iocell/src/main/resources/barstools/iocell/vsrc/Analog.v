// See LICENSE for license details

`timescale 1ns/1ps

module AnalogConst #(CONST, WIDTH) (
    output [WIDTH-1:0] io
);

    assign io = CONST;

endmodule
