
import "DPI-C" function longint plusarg_file_mem_init(
    input string filename,
    input bit writeable,
    input int addr_bits,
    input int data_bits
);

import "DPI-C" function void plusarg_file_mem_read(
    input longint ptr,
    input longint address,
    output longint data
);

import "DPI-C" function void plusarg_file_mem_write(
    input longint ptr,
    input longint address,
    input longint data
);

module plusarg_file_mem #(
    parameter string PLUSARG,
    parameter int ADDR_BITS,
    parameter bit READONLY,
    parameter int DATA_BYTES
) (
    input                     clock,
    input                     reset,
    input                     mem_req_valid,
    input  [ADDR_BITS-1:0]    mem_req_addr,
    input  [DATA_BYTES*8-1:0] mem_req_data,
    input                     mem_req_r_wb,
    output [DATA_BYTES*8-1:0] mem_resp_data
);

    // Stores the file name for the binary memory contents of the flash
    string filename;

    // This is a pointer to the C FileMem object
    longint dev_ptr;

    initial begin
        assert(ADDR_BITS <= 64);
        assert((DATA_BYTES == 1) || (DATA_BYTES == 2) || (DATA_BYTES == 4) || (DATA_BYTES == 8));
        if ($value$plusargs($sformatf("%s=%%s", PLUSARG), filename)) begin
            dev_ptr = plusarg_file_mem_init(filename, !READONLY, ADDR_BITS, DATA_BYTES);
        end else begin
            // Workaround for verilator to write to STDERR
            $fwrite(32'h80000002, "No memory image provided. Use +%s=<file> to specify.\n", PLUSARG);
            $fatal;
        end
    end

    reg [63:0] mem_resp_data_reg;
    logic [63+ADDR_BITS:0] mem_req_addr_zext_pad;
    logic [63:0] mem_req_addr_zext;
    logic [63+DATA_BYTES*8:0] mem_req_data_zext_pad;
    logic [63:0] mem_req_data_zext;

    assign mem_resp_data = mem_resp_data_reg[DATA_BYTES*8-1:0];
    assign mem_req_addr_zext_pad = {64'd0, mem_req_addr};
    assign mem_req_addr_zext = mem_req_addr_zext_pad[63:0];
    assign mem_req_data_zext_pad = {64'd0, mem_req_data};
    assign mem_req_data_zext = mem_req_data_zext_pad[63:0];

    always @(posedge clock) begin
        if ((!reset) && mem_req_valid) begin
            if (mem_req_r_wb) begin
                plusarg_file_mem_read(dev_ptr, mem_req_addr_zext, mem_resp_data_reg);
            end else begin
                plusarg_file_mem_write(dev_ptr, mem_req_addr_zext, mem_req_data_zext);
            end
        end
    end

endmodule

