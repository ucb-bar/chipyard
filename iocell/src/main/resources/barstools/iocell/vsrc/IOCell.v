// See LICENSE for license details

`timescale 1ns/1ps

module ExampleAnalogIOCell(
    inout pad,
    inout core
);

    assign core = 1'bz;
    assign pad = core;

endmodule

module ExampleDigitalGPIOCell(
    inout pad,
    output i,
    input ie,
    input o,
    input oe
);

    assign pad = oe ? o : 1'bz;
    assign i = ie ? pad : 1'b0;

endmodule

module ExampleDigitalInIOCell(
    input pad,
    output i,
    input ie
);

    assign i = ie ? pad : 1'b0;

endmodule

module ExampleDigitalOutIOCell(
    output pad,
    input o,
    output oe
);

    assign pad = oe ? o : 1'bz;

endmodule
