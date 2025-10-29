`define DATA_WIDTH 8

import "DPI-C" function void uart_init(
                                       input string filename,
                                       input int    uartno,
                                       input int    forcepty
);

import "DPI-C" function void uart_tick
(
 input int   uartno,
 input bit   serial_out_valid,
 output bit  serial_out_ready,
 input byte  serial_out_bits,

 output bit  serial_in_valid,
 input bit   serial_in_ready,
 output byte serial_in_bits
);

module SimUART #(UARTNO=0, FORCEPTY=0) (
    input              clock,
    input              reset,

    input                    serial_out_valid,
    output                   serial_out_ready,
    input  [`DATA_WIDTH-1:0] serial_out_bits,

    output                   serial_in_valid,
    input                    serial_in_ready,
    output [`DATA_WIDTH-1:0] serial_in_bits
);

   wire                      __in_ready;
   bit                       __in_valid;
   byte                      __in_bits;

   bit                       __out_ready;
   wire                      __out_valid;
   wire [`DATA_WIDTH-1:0]    __out_bits;

   string                    __uartlog;

   initial begin
      $value$plusargs("uartlog=%s", __uartlog);
      uart_init(__uartlog, UARTNO, FORCEPTY);
   end

   reg __in_valid_reg;
   reg [`DATA_WIDTH-1:0] __in_bits_reg;

   reg                   __out_ready_reg;


   
   // Evaluate the signals on the positive edge
   always @(posedge clock) begin
      if (reset) begin
         __in_valid = 0;
         __out_ready = 0;

         __in_valid_reg <= 0;
         __in_bits_reg <= 0;
         __out_ready_reg <= 0;
      end else begin
         uart_tick(
                   UARTNO,
                   __out_valid,
                   __out_ready,
                   __out_bits,
                   __in_valid,
                   __in_ready,
                   __in_bits
                   );

         __out_ready_reg <= __out_ready;
         __in_valid_reg  <= __in_valid;
         __in_bits_reg   <= __in_bits;
      end
   end // always @ (posedge clock)

   assign serial_in_valid  = __in_valid_reg;
   assign serial_in_bits   = __in_bits_reg;
   assign __in_ready = serial_in_ready;

   assign serial_out_ready = __out_ready_reg;
   assign __out_valid = serial_out_valid;
   assign __out_bits = serial_out_bits;

endmodule
